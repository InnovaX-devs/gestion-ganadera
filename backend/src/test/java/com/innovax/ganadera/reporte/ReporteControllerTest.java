package com.innovax.ganadera.reporte;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #32: reportes de evolución de pesos y existencias para SENASA. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReporteControllerTest {

    /**
     * Fechas relativas a "ayer" en cualquier zona horaria: si el test corre de noche en UTC, "hoy"
     * puede ser mañana para el servidor (pasó en el #30).
     */
    private static final LocalDate AYER = LocalDate.now().minusDays(1);
    private static final LocalDate D0 = AYER.minusDays(60);
    private static final LocalDate D1 = AYER.minusDays(30);
    private static final LocalDate D2 = AYER.minusDays(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private String token;
    private String est;
    private String lote;
    private String n1;
    private String n2;
    private String v1;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        est = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza",
                "renspa", "01.234.5.67890/01", "localidad", "Río Cuarto", "provincia", "Córdoba"))
            .andExpect(status().isCreated()), "id");
        lote = crear("lotes", Map.of("nombre", "Recría"));
        n1 = crearAnimal("N-1", "MACHO", "NOVILLO", lote);
        n2 = crearAnimal("N-2", "MACHO", "NOVILLO", null);
        v1 = crearAnimal("V-1", "HEMBRA", "VACA", null);

        // N-1 sube 45 kg en 50 días (0,9 kg/día); N-2 sube 30 kg en 50 días (0,6 kg/día); V-1 se pesó una vez
        pesar(n1, D0, 300);
        pesar(n1, D1, 330);
        pesar(n1, D2, 345);
        pesar(n2, D0, 280);
        pesar(n2, D2, 310);
        pesar(v1, D1, 450);
        // Un pesaje anulado no cuenta
        String anulado = pesar(n2, D1, 999);
        String eventoId = jdbc.queryForObject("SELECT id FROM evento WHERE operacion_id = ?", String.class,
            UUID.fromString(anulado));
        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Evolución de pesos ----------

    @Test
    void calculaLaGananciaDiariaDeCadaAnimalYLosPromedios() throws Exception {
        pesos("desde=" + D0 + "&hasta=" + AYER)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.desde").value(D0.toString()))
            .andExpect(jsonPath("$.resumen.animales").value(3))
            .andExpect(jsonPath("$.resumen.pesajes").value(6))
            .andExpect(jsonPath("$.resumen.animalesConGanancia").value(2))
            // (300 + 280 + 450) / 3 y (345 + 310 + 450) / 3
            .andExpect(jsonPath("$.resumen.pesoInicialPromedio").value(343.33))
            .andExpect(jsonPath("$.resumen.pesoFinalPromedio").value(368.33))
            .andExpect(jsonPath("$.resumen.gananciaDiariaPromedio").value(0.75))
            // Por animal, ordenados por caravana
            .andExpect(jsonPath("$.porAnimal[0].caravana").value("N-1"))
            .andExpect(jsonPath("$.porAnimal[0].lote").value("Recría"))
            .andExpect(jsonPath("$.porAnimal[0].pesajes").value(3))
            .andExpect(jsonPath("$.porAnimal[0].pesoInicial").value(300.0))
            .andExpect(jsonPath("$.porAnimal[0].pesoFinal").value(345.0))
            .andExpect(jsonPath("$.porAnimal[0].dias").value(50))
            .andExpect(jsonPath("$.porAnimal[0].variacionKg").value(45.0))
            .andExpect(jsonPath("$.porAnimal[0].gananciaDiaria").value(0.9))
            .andExpect(jsonPath("$.porAnimal[1].gananciaDiaria").value(0.6))
            // Una sola pesada: sin ganancia
            .andExpect(jsonPath("$.porAnimal[2].caravana").value("V-1"))
            .andExpect(jsonPath("$.porAnimal[2].gananciaDiaria").doesNotExist())
            .andExpect(jsonPath("$.porAnimal[2].dias").doesNotExist());
    }

    @Test
    void agrupaPorFechaYPorCategoria() throws Exception {
        pesos("desde=" + D0 + "&hasta=" + AYER)
            .andExpect(jsonPath("$.porFecha.length()").value(3))
            .andExpect(jsonPath("$.porFecha[0].fecha").value(D0.toString()))
            .andExpect(jsonPath("$.porFecha[0].pesajes").value(2))
            .andExpect(jsonPath("$.porFecha[0].kgPromedio").value(290.0))
            .andExpect(jsonPath("$.porFecha[1].kgPromedio").value(390.0))
            .andExpect(jsonPath("$.porFecha[2].kgPromedio").value(327.5))
            .andExpect(jsonPath("$.porCategoria.length()").value(2))
            .andExpect(jsonPath("$.porCategoria[0].categoria").value("VACA"))
            .andExpect(jsonPath("$.porCategoria[0].gananciaDiariaPromedio").doesNotExist())
            .andExpect(jsonPath("$.porCategoria[1].categoria").value("NOVILLO"))
            .andExpect(jsonPath("$.porCategoria[1].animales").value(2))
            .andExpect(jsonPath("$.porCategoria[1].gananciaDiariaPromedio").value(0.75));
    }

    @Test
    void filtraPorPeriodoLoteYCategoria() throws Exception {
        // Desde D1: N-1 pasa de 330 a 345 en 20 días
        pesos("desde=" + D1 + "&hasta=" + AYER)
            .andExpect(jsonPath("$.porAnimal[0].pesoInicial").value(330.0))
            .andExpect(jsonPath("$.porAnimal[0].gananciaDiaria").value(0.75));
        pesos("desde=" + D0 + "&hasta=" + AYER + "&loteId=" + lote)
            .andExpect(jsonPath("$.porAnimal.length()").value(1))
            .andExpect(jsonPath("$.porAnimal[0].caravana").value("N-1"));
        pesos("desde=" + D0 + "&hasta=" + AYER + "&categoria=VACA")
            .andExpect(jsonPath("$.resumen.animales").value(1))
            .andExpect(jsonPath("$.porAnimal[0].caravana").value("V-1"));
    }

    @Test
    void sinFechasUsaLosUltimos90Dias() throws Exception {
        pesos("")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumen.animales").value(3));
    }

    @Test
    void validaElPeriodoYLosFiltros() throws Exception {
        pesos("desde=" + AYER + "&hasta=" + D0)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("desde"));
        pesos("desde=2020-01-01&hasta=" + AYER).andExpect(status().isBadRequest());
        pesos("categoria=CABRA").andExpect(status().isBadRequest());
        pesos("desde=ayer").andExpect(status().isBadRequest());
    }

    // ---------- Existencias para SENASA ----------

    @Test
    void existenciasTraeElEstablecimientoElStockYLaPlanillaDeAnimales() throws Exception {
        String vendido = crearAnimal("Z-9", "HEMBRA", "VACA", null);
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendido));

        api.get(token, "/api/establecimientos/" + est + "/reportes/existencias")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.establecimiento.nombre").value("La Esperanza"))
            .andExpect(jsonPath("$.establecimiento.renspa").value("01.234.5.67890/01"))
            .andExpect(jsonPath("$.establecimiento.provincia").value("Córdoba"))
            .andExpect(jsonPath("$.generadoEn").exists())
            .andExpect(jsonPath("$.stock.total").value(3))
            .andExpect(jsonPath("$.stock.porCategoria.NOVILLO").value(2))
            // Solo activos, por lote (los sin lote al final) y por caravana
            .andExpect(jsonPath("$.animales.length()").value(3))
            .andExpect(jsonPath("$.animales[0].caravana").value("N-1"))
            .andExpect(jsonPath("$.animales[0].lote").value("Recría"))
            .andExpect(jsonPath("$.animales[1].caravana").value("N-2"))
            .andExpect(jsonPath("$.animales[1].lote").doesNotExist())
            .andExpect(jsonPath("$.animales[2].caravana").value("V-1"));
    }

    // ---------- Permisos ----------

    @Test
    void otraOrganizacionNoVeLosReportes() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        api.get(tokenB, "/api/establecimientos/" + est + "/reportes/pesos").andExpect(status().isNotFound());
        api.get(tokenB, "/api/establecimientos/" + est + "/reportes/existencias").andExpect(status().isNotFound());
        api.get(null, "/api/establecimientos/" + est + "/reportes/pesos").andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private ResultActions pesos(String parametros) throws Exception {
        return api.get(token, "/api/establecimientos/" + est + "/reportes/pesos?" + parametros);
    }

    private String eventos() {
        return "/api/establecimientos/" + est + "/eventos";
    }

    private String pesar(String animal, LocalDate fecha, int kg) throws Exception {
        return api.leer(api.post(token, eventos(), Map.of("tipo", "PESAJE", "fecha", fecha.toString(),
                "animalIds", List.of(animal), "datos", Map.of("kg", kg)))
            .andExpect(status().isCreated()), "operacionId");
    }

    private String crearAnimal(String caravana, String sexo, String categoria, String loteId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("caravana", caravana, "sexo", sexo, "categoria", categoria));
        if (loteId != null) body.put("loteId", loteId);
        return crear("animales", body);
    }

    private String crear(String recurso, Map<String, Object> body) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos/" + est + "/" + recurso, body)
            .andExpect(status().isCreated()), "id");
    }
}
