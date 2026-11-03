package com.innovax.ganadera.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Panel de administración (#35): cuentas y planes. El login del panel está en {@link AdminAuthTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    private static final String URL = "/api/admin/organizaciones";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private String tokenAdmin;
    private String tokenCliente;
    private String clienteId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenAdmin = api.loginAdmin();
        registrar("Innovax", "otro@campo.com");
        // Se crean en el mismo milisegundo: la primera se corre al pasado para que el orden sea fijo
        jdbc.update("UPDATE organizacion SET created_at = created_at - INTERVAL '1' DAY");
        tokenCliente = registrar("Estancia La Esperanza", "dueno@campo.com");
        clienteId = api.leer(api.get(tokenCliente, "/api/auth/me"), "organizacionId");
        String est = api.leer(api.post(tokenCliente, "/api/establecimientos", Map.of("nombre", "La Esperanza")), "id");
        api.post(tokenCliente, "/api/establecimientos/" + est + "/animales",
            Map.of("caravana", "A-1", "sexo", "HEMBRA", "categoria", "VACA")).andExpect(status().isCreated());
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    @Test
    void elAdministradorVeTodasLasCuentasConSuUso() throws Exception {
        api.get(tokenAdmin, URL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            // La más nueva primero
            .andExpect(jsonPath("$[0].nombre").value("Estancia La Esperanza"))
            .andExpect(jsonPath("$[0].duenoEmail").value("dueno@campo.com"))
            .andExpect(jsonPath("$[0].duenoNombre").value("Test"))
            .andExpect(jsonPath("$[0].plan.uso.animales").value(1))
            .andExpect(jsonPath("$[0].plan.uso.establecimientos").value(1))
            .andExpect(jsonPath("$[0].plan.enPrueba").value(true))
            .andExpect(jsonPath("$[1].nombre").value("Innovax"))
            .andExpect(jsonPath("$[1].plan.uso.animales").value(0));
    }

    @Test
    void cambiaElPlanYLaPruebaDeUnaCuenta() throws Exception {
        api.put(tokenAdmin, URL + "/" + clienteId + "/plan", Map.of("planCodigo", "productor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.plan.enPrueba").value(false))
            .andExpect(jsonPath("$.plan.plan.codigo").value("PRODUCTOR"));
        // La cuenta lo ve en Mi plan
        api.get(tokenCliente, "/api/plan").andExpect(jsonPath("$.plan.codigo").value("PRODUCTOR"));

        Map<String, Object> conPrueba = new HashMap<>();
        conPrueba.put("planCodigo", "INICIAL");
        conPrueba.put("pruebaHasta", HOY.plusDays(14).toString());
        api.put(tokenAdmin, URL + "/" + clienteId + "/plan", conPrueba)
            .andExpect(jsonPath("$.plan.enPrueba").value(true))
            .andExpect(jsonPath("$.plan.diasDePrueba").value(15))
            .andExpect(jsonPath("$.plan.pruebaHasta").value(HOY.plusDays(14).toString()));
    }

    @Test
    void unaPruebaTerminadaSeSigueViendoConSuFecha() throws Exception {
        Map<String, Object> vencida = new HashMap<>();
        vencida.put("planCodigo", "INICIAL");
        vencida.put("pruebaHasta", HOY.minusDays(3).toString());
        api.put(tokenAdmin, URL + "/" + clienteId + "/plan", vencida)
            .andExpect(jsonPath("$.plan.enPrueba").value(false))
            .andExpect(jsonPath("$.plan.pruebaHasta").value(HOY.minusDays(3).toString()));
    }

    @Test
    void rechazaUnPlanQueNoExiste() throws Exception {
        api.put(tokenAdmin, URL + "/" + clienteId + "/plan", Map.of("planCodigo", "ORO"))
            .andExpect(status().isUnprocessableEntity());
        api.put(tokenAdmin, URL + "/" + clienteId + "/plan", Map.of("planCodigo", ""))
            .andExpect(status().isBadRequest());
        api.put(tokenAdmin, URL + "/00000000-0000-0000-0000-000000000000/plan", Map.of("planCodigo", "INICIAL"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unClienteNoPuedeEntrar() throws Exception {
        api.get(tokenCliente, URL).andExpect(status().isForbidden());
        api.put(tokenCliente, URL + "/" + clienteId + "/plan", Map.of("planCodigo", "EMPRESA"))
            .andExpect(status().isForbidden());
        api.get(null, URL).andExpect(status().isUnauthorized());
    }

    private String registrar(String organizacion, String email) throws Exception {
        return api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", organizacion,
            "nombre", "Test", "email", email, "password", ApiTestClient.PASSWORD)), "accessToken");
    }
}
