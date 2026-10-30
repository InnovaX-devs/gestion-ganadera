package com.innovax.ganadera.clima;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #29: clima del establecimiento, con un Open-Meteo falso. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClimaControllerTest {

    /** Cuenta las consultas y permite simular que el servicio se cae. */
    static class ProveedorFalso implements ProveedorClima {
        int pronosticos;
        int busquedas;
        boolean caido;
        double ultimaLatitud;

        @Override
        public Pronostico pronostico(double latitud, double longitud, int dias) {
            pronosticos++;
            ultimaLatitud = latitud;
            if (caido) throw new IllegalStateException("Open-Meteo caído");
            return new Pronostico(
                new Actual(LocalDateTime.of(2026, 10, 30, 14, 15), new BigDecimal("24.3"), new BigDecimal("23.1"),
                    41, new BigDecimal("18.7"), BigDecimal.ZERO, 2),
                List.of(
                    dia(0, 2, "0"), dia(1, 61, "8.4"), dia(2, 95, "21"), dia(3, 3, null), dia(4, 0, "0.25")));
        }

        @Override
        public Optional<Lugar> buscarLocalidad(String localidad, String provincia) {
            busquedas++;
            if (caido) throw new IllegalStateException("Open-Meteo caído");
            return localidad.equalsIgnoreCase("Río Cuarto")
                ? Optional.of(new Lugar(-33.12, -64.35, "Río Cuarto, Córdoba"))
                : Optional.empty();
        }

        private static Dia dia(int desde, int codigo, String lluvia) {
            return new Dia(LocalDate.of(2026, 10, 30).plusDays(desde), codigo, new BigDecimal("27"),
                new BigDecimal("12"), lluvia == null ? null : new BigDecimal(lluvia), 40);
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        ProveedorFalso proveedorFalso() {
            return new ProveedorFalso();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProveedorFalso proveedor;

    @Autowired
    private ClimaService service;

    private ApiTestClient api;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        service.vaciarCache();
        proveedor.pronosticos = 0;
        proveedor.busquedas = 0;
        proveedor.caido = false;
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    @Test
    void conCoordenadasDevuelveElClimaActualYElPronostico() throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "latitud", -33.5, "longitud", -64.1));

        clima(id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ubicacion.origen").value("COORDENADAS"))
            .andExpect(jsonPath("$.ubicacion.latitud").value(-33.5))
            .andExpect(jsonPath("$.actual.temperatura").value(24.3))
            .andExpect(jsonPath("$.actual.humedad").value(41))
            .andExpect(jsonPath("$.actual.codigo").value(2))
            .andExpect(jsonPath("$.dias.length()").value(5))
            .andExpect(jsonPath("$.dias[1].fecha").value("2026-10-31"))
            .andExpect(jsonPath("$.dias[1].lluviaMm").value(8.4))
            .andExpect(jsonPath("$.dias[3].lluviaMm").doesNotExist())
            // 0 + 8,4 + 21 + 0,25 (el día sin dato no suma)
            .andExpect(jsonPath("$.lluviaEsperadaMm").value(29.7))
            .andExpect(jsonPath("$.fuente").value("Open-Meteo"))
            .andExpect(jsonPath("$.consultadoEn").exists());
        assertThat(proveedor.ultimaLatitud).isEqualTo(-33.5);
        assertThat(proveedor.busquedas).isZero();
    }

    @Test
    void sinCoordenadasBuscaLaLocalidad() throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "localidad", "Río Cuarto", "provincia", "Córdoba"));

        clima(id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ubicacion.origen").value("LOCALIDAD"))
            .andExpect(jsonPath("$.ubicacion.nombre").value("Río Cuarto, Córdoba"))
            .andExpect(jsonPath("$.ubicacion.latitud").value(-33.12));
    }

    @Test
    void sinUbicacionOLocalidadDesconocidaDevuelve422() throws Exception {
        String sinNada = crear(Map.of("nombre", "Sin datos"));
        String inventada = crear(Map.of("nombre", "Otro", "localidad", "Pueblo Inventado"));

        clima(sinNada)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(ClimaService.SIN_UBICACION));
        clima(inventada)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Pueblo Inventado")));
    }

    @Test
    void reutilizaElPronosticoYLaLocalidadSinVolverAConsultar() throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "localidad", "Río Cuarto", "provincia", "Córdoba"));
        // Otro campo a pocos metros comparte el pronóstico
        String vecino = crear(Map.of("nombre", "El Vecino", "latitud", -33.121, "longitud", -64.349));

        clima(id).andExpect(status().isOk());
        clima(id).andExpect(status().isOk());
        clima(vecino)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ubicacion.origen").value("COORDENADAS"));

        assertThat(proveedor.pronosticos).isEqualTo(1);
        assertThat(proveedor.busquedas).isEqualTo(1);
    }

    @Test
    void siElServicioNoRespondeDevuelve503() throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "latitud", -33.5, "longitud", -64.1));
        proveedor.caido = true;

        clima(id)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("clima no responde")));
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void todosLosRolesVenElClima(Rol rol) throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "latitud", -33.5, "longitud", -64.1));

        api.get(tokenConRol(rol, id), url(id)).andExpect(status().isOk());
    }

    @Test
    void otraOrganizacionOEstablecimientoInexistenteDevuelve404() throws Exception {
        String id = crear(Map.of("nombre", "La Esperanza", "latitud", -33.5, "longitud", -64.1));
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, url(id)).andExpect(status().isNotFound());
        clima(UUID.randomUUID().toString()).andExpect(status().isNotFound());
        assertThat(proveedor.pronosticos).isZero();
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, url(UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private org.springframework.test.web.servlet.ResultActions clima(String id) throws Exception {
        return api.get(token, url(id));
    }

    private static String url(String establecimientoId) {
        return "/api/establecimientos/" + establecimientoId + "/clima";
    }

    private String crear(Map<String, Object> datos) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos", new HashMap<>(datos))
            .andExpect(status().isCreated()), "id");
    }

    /** Usuario nuevo de la misma organización con ese rol en el establecimiento (como en StockControllerTest). */
    private String tokenConRol(Rol rol, String establecimientoId) throws Exception {
        if (rol == Rol.DUENO) return token;
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, "/api/establecimientos/" + establecimientoId + "/miembros",
                Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
