package com.innovax.ganadera.establecimiento;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstablecimientoControllerTest {

    private static final String URL = "/api/establecimientos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private String tokenA;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenA = api.registrarCuenta("Organización A");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Alta y consulta ----------

    @Test
    void creaYDevuelveElEstablecimientoConTodosSusDatos() throws Exception {
        var body = establecimiento("La Esperanza");
        body.put("localidad", "  Río Cuarto ");
        body.put("provincia", "Córdoba");
        body.put("superficieHa", 1250.5);
        body.put("renspa", "01.001.0.00001/00");
        body.put("latitud", -33.123456);
        body.put("longitud", -64.349876);

        String id = api.leer(api.post(tokenA, URL, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("La Esperanza"))
            .andExpect(jsonPath("$.localidad").value("Río Cuarto"))
            .andExpect(jsonPath("$.superficieHa").value(1250.5)), "id");

        api.get(tokenA, URL + "/" + id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.renspa").value("01.001.0.00001/00"))
            .andExpect(jsonPath("$.latitud").value(-33.123456));
    }

    @Test
    void listaOrdenadoPorNombre() throws Exception {
        api.post(tokenA, URL, establecimiento("Santa Rosa"));
        api.post(tokenA, URL, establecimiento("El Ombú"));
        api.post(tokenA, URL, establecimiento("La Esperanza"));

        api.get(tokenA, URL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].nombre").value("El Ombú"))
            .andExpect(jsonPath("$[2].nombre").value("Santa Rosa"));
    }

    // ---------- Validaciones ----------

    @Test
    void sinNombreDevuelve400() throws Exception {
        api.post(tokenA, URL, establecimiento("   "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("nombre"));
    }

    @Test
    void superficieYCoordenadasInvalidasDevuelven400() throws Exception {
        var body = establecimiento("La Esperanza");
        body.put("superficieHa", -10);
        body.put("latitud", 95);
        body.put("longitud", -200);

        api.post(tokenA, URL, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void nombreRepetidoDevuelve409SinImportarMayusculas() throws Exception {
        api.post(tokenA, URL, establecimiento("La Esperanza")).andExpect(status().isCreated());

        api.post(tokenA, URL, establecimiento("LA ESPERANZA"))
            .andExpect(status().isConflict());
    }

    @Test
    void otraOrganizacionPuedeUsarElMismoNombre() throws Exception {
        api.post(tokenA, URL, establecimiento("La Esperanza")).andExpect(status().isCreated());
        String tokenB = api.registrarCuenta("Organización B");

        api.post(tokenB, URL, establecimiento("La Esperanza")).andExpect(status().isCreated());
    }

    // ---------- Edición y baja ----------

    @Test
    void editaElEstablecimiento() throws Exception {
        String id = crear(tokenA, "La Esperanza");
        var cambios = establecimiento("La Nueva Esperanza");
        cambios.put("provincia", "Córdoba");

        api.put(tokenA, URL + "/" + id, cambios)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("La Nueva Esperanza"))
            .andExpect(jsonPath("$.provincia").value("Córdoba"));
    }

    @Test
    void editarConElNombreDeOtroEstablecimientoDevuelve409PeroConElPropioNo() throws Exception {
        String id = crear(tokenA, "La Esperanza");
        crear(tokenA, "El Ombú");

        api.put(tokenA, URL + "/" + id, establecimiento("el ombú")).andExpect(status().isConflict());
        api.put(tokenA, URL + "/" + id, establecimiento("La Esperanza")).andExpect(status().isOk());
    }

    @Test
    void bajaLogicaLoSacaDelListadoYLiberaElNombre() throws Exception {
        String id = crear(tokenA, "La Esperanza");

        api.delete(tokenA, URL + "/" + id).andExpect(status().isNoContent());

        api.get(tokenA, URL).andExpect(jsonPath("$.length()").value(0));
        api.get(tokenA, URL + "/" + id).andExpect(status().isNotFound());
        api.post(tokenA, URL, establecimiento("La Esperanza")).andExpect(status().isCreated());
    }

    // ---------- Aislamiento entre organizaciones ----------

    @Test
    void otraOrganizacionNoPuedeVerEditarNiBorrar() throws Exception {
        String id = crear(tokenA, "La Esperanza");
        String tokenB = api.registrarCuenta("Organización B");
        String urlDeA = URL + "/" + id;

        api.get(tokenB, URL).andExpect(jsonPath("$.length()").value(0));
        api.get(tokenB, urlDeA).andExpect(status().isNotFound());
        api.put(tokenB, urlDeA, establecimiento("Robado")).andExpect(status().isNotFound());
        api.delete(tokenB, urlDeA).andExpect(status().isNotFound());

        // Sigue intacto para su dueño
        api.get(tokenA, urlDeA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("La Esperanza"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, URL).andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private Map<String, Object> establecimiento(String nombre) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", nombre);
        return body;
    }

    private String crear(String token, String nombre) throws Exception {
        return api.leer(api.post(token, URL, establecimiento(nombre)).andExpect(status().isCreated()), "id");
    }
}
