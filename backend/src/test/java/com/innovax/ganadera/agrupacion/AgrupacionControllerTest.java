package com.innovax.ganadera.agrupacion;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Lotes y rodeos comparten comportamiento: cada test corre para los dos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgrupacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private String token;
    private String establecimientoId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        establecimientoId = crearEstablecimiento(token, "La Esperanza");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- ABM ----------

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void creaYListaOrdenadoPorNombre(String tipo) throws Exception {
        var body = agrupacion("Recría 2026");
        body.put("descripcion", "  Terneros destetados en marzo ");

        api.post(token, url(tipo), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Recría 2026"))
            .andExpect(jsonPath("$.descripcion").value("Terneros destetados en marzo"))
            .andExpect(jsonPath("$.establecimientoId").value(establecimientoId))
            .andExpect(jsonPath("$.cantidadAnimales").value(0));
        api.post(token, url(tipo), agrupacion("Cría"));

        api.get(token, url(tipo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nombre").value("Cría"))
            .andExpect(jsonPath("$[1].nombre").value("Recría 2026"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void sinNombreDevuelve400(String tipo) throws Exception {
        api.post(token, url(tipo), agrupacion("  "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void nombreRepetidoEnElMismoEstablecimientoDevuelve409(String tipo) throws Exception {
        api.post(token, url(tipo), agrupacion("Cría")).andExpect(status().isCreated());

        api.post(token, url(tipo), agrupacion("CRÍA")).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void elMismoNombreSePuedeUsarEnOtroEstablecimiento(String tipo) throws Exception {
        api.post(token, url(tipo), agrupacion("Cría")).andExpect(status().isCreated());
        String otro = crearEstablecimiento(token, "El Ombú");

        api.post(token, url(otro, tipo), agrupacion("Cría")).andExpect(status().isCreated());
        api.get(token, url(otro, tipo)).andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void editaYDaDeBaja(String tipo) throws Exception {
        String id = crear(tipo, "Cría");

        api.put(token, url(tipo) + "/" + id, agrupacion("Cría general"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Cría general"));

        api.delete(token, url(tipo) + "/" + id).andExpect(status().isNoContent());
        api.get(token, url(tipo)).andExpect(jsonPath("$.length()").value(0));
        api.get(token, url(tipo) + "/" + id).andExpect(status().isNotFound());
        api.post(token, url(tipo), agrupacion("Cría general")).andExpect(status().isCreated());
    }

    // ---------- Pertenencia al establecimiento ----------

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void noSeAccedeAUnLoteORodeoDesdeOtroEstablecimiento(String tipo) throws Exception {
        String id = crear(tipo, "Cría");
        String otro = crearEstablecimiento(token, "El Ombú");

        api.get(token, url(otro, tipo) + "/" + id).andExpect(status().isNotFound());
        api.put(token, url(otro, tipo) + "/" + id, agrupacion("X")).andExpect(status().isNotFound());
        api.delete(token, url(otro, tipo) + "/" + id).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void establecimientoInexistenteDevuelve404(String tipo) throws Exception {
        String inexistente = java.util.UUID.randomUUID().toString();

        api.get(token, url(inexistente, tipo)).andExpect(status().isNotFound());
        api.post(token, url(inexistente, tipo), agrupacion("Cría")).andExpect(status().isNotFound());
    }

    // ---------- Aislamiento entre organizaciones ----------

    @ParameterizedTest
    @ValueSource(strings = {"lotes", "rodeos"})
    void otraOrganizacionNoPuedeVerCrearEditarNiBorrar(String tipo) throws Exception {
        String id = crear(tipo, "Cría");
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, url(tipo)).andExpect(status().isNotFound());
        api.post(tokenB, url(tipo), agrupacion("Intruso")).andExpect(status().isNotFound());
        api.get(tokenB, url(tipo) + "/" + id).andExpect(status().isNotFound());
        api.put(tokenB, url(tipo) + "/" + id, agrupacion("Robado")).andExpect(status().isNotFound());
        api.delete(tokenB, url(tipo) + "/" + id).andExpect(status().isNotFound());

        api.get(token, url(tipo))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nombre").value("Cría"));
    }

    // ---------- Baja del establecimiento ----------

    @Test
    void noSePuedeEliminarUnEstablecimientoConLotesORodeos() throws Exception {
        String loteId = crear("lotes", "Recría");
        String rodeoId = crear("rodeos", "Cría");
        String urlEstablecimiento = "/api/establecimientos/" + establecimientoId;

        api.delete(token, urlEstablecimiento)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message")
                .value("No se puede eliminar: todavía tiene 1 lote y 1 rodeo. Eliminalos primero."));

        api.delete(token, url("lotes") + "/" + loteId).andExpect(status().isNoContent());
        api.delete(token, url("rodeos") + "/" + rodeoId).andExpect(status().isNoContent());
        api.delete(token, urlEstablecimiento).andExpect(status().isNoContent());
    }

    // ---------- Helpers ----------

    private String url(String tipo) {
        return url(establecimientoId, tipo);
    }

    private static String url(String establecimiento, String tipo) {
        return "/api/establecimientos/" + establecimiento + "/" + tipo;
    }

    private static Map<String, Object> agrupacion(String nombre) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", nombre);
        return body;
    }

    private String crear(String tipo, String nombre) throws Exception {
        return api.leer(api.post(token, url(tipo), agrupacion(nombre)).andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }
}
