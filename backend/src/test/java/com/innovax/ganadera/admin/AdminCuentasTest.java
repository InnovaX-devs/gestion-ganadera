package com.innovax.ganadera.admin;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import com.innovax.ganadera.support.FakeEmailSender;

/** Panel de administración (#35): crear cuentas con link de activación y suspenderlas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCuentasTest {

    private static final String URL = "/api/admin/organizaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeEmailSender emails;

    private ApiTestClient api;
    private String tokenAdmin;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        emails.limpiar();
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenAdmin = api.loginAdmin();
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Crear cuenta ----------

    @Test
    void creaLaCuentaYElDuenoEligeSuContrasenaDesdeElLink() throws Exception {
        String id = api.leer(api.post(tokenAdmin, URL, cuenta("juan@campo.com", "PRODUCTOR"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("Estancia San Juan"))
            .andExpect(jsonPath("$.duenoEmail").value("juan@campo.com"))
            .andExpect(jsonPath("$.duenoPendiente").value(true))
            .andExpect(jsonPath("$.plan.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.plan.enPrueba").value(false)), "id");

        // Le llegó el email con el link
        assertEquals("juan@campo.com", emails.ultimo().destinatario());
        String token = emails.ultimoToken();

        // Antes de activar no puede entrar con ninguna contraseña
        api.post(null, "/api/auth/login", Map.of("email", "juan@campo.com", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isUnauthorized());

        api.get(null, "/api/activacion/" + token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("juan@campo.com"))
            .andExpect(jsonPath("$.nombre").value("Juan"))
            .andExpect(jsonPath("$.organizacionNombre").value("Estancia San Juan"));
        String tokenJuan = api.leer(api.post(null, "/api/activacion/" + token,
                Map.of("password", ApiTestClient.PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usuario.organizacionId").value(id)), "accessToken");

        // Ya activada: el link no sirve más, el login sí, y el panel lo muestra
        api.get(tokenJuan, "/api/plan").andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"));
        api.post(null, "/api/activacion/" + token, Map.of("password", ApiTestClient.PASSWORD))
            .andExpect(status().isUnprocessableEntity());
        api.post(null, "/api/auth/login", Map.of("email", "juan@campo.com", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isOk());
        api.get(tokenAdmin, URL)
            .andExpect(jsonPath("$[?(@.duenoEmail == 'juan@campo.com')].duenoPendiente", contains(false)));
        api.post(tokenAdmin, URL + "/" + id + "/reenviar-activacion", Map.of())
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reenviarInvalidaElLinkAnterior() throws Exception {
        String id = api.leer(api.post(tokenAdmin, URL, cuenta("juan@campo.com", "INICIAL")), "id");
        String viejo = emails.ultimoToken();

        api.post(tokenAdmin, URL + "/" + id + "/reenviar-activacion", Map.of()).andExpect(status().isNoContent());
        String nuevo = emails.ultimoToken();

        api.get(null, "/api/activacion/" + viejo).andExpect(status().isNotFound());
        api.get(null, "/api/activacion/" + nuevo).andExpect(status().isOk());
    }

    @Test
    void validaLosDatosDeLaCuentaNueva() throws Exception {
        registrar("Otra", "repetido@campo.com");
        api.post(tokenAdmin, URL, cuenta("repetido@campo.com", "INICIAL")).andExpect(status().isConflict());
        api.post(tokenAdmin, URL, cuenta("no-es-email", "INICIAL")).andExpect(status().isBadRequest());
        api.post(tokenAdmin, URL, cuenta("nuevo@campo.com", "ORO")).andExpect(status().isUnprocessableEntity());
        api.post(null, "/api/activacion/token-inventado", Map.of("password", ApiTestClient.PASSWORD))
            .andExpect(status().isNotFound());
    }

    @Test
    void conPruebaGratis() throws Exception {
        var body = cuenta("juan@campo.com", "INICIAL");
        body.put("pruebaHasta", LocalDate.now().plusDays(10).toString());
        api.post(tokenAdmin, URL, body).andExpect(jsonPath("$.plan.enPrueba").value(true));
    }

    // ---------- Suspender ----------

    @Test
    void unaCuentaSuspendidaNoPuedeEntrarYSeReactiva() throws Exception {
        String tokenCliente = registrar("Estancia", "cliente@campo.com");
        String clienteId = api.leer(api.get(tokenCliente, "/api/auth/me"), "organizacionId");
        String refresh = api.leer(api.post(null, "/api/auth/login",
            Map.of("email", "cliente@campo.com", "password", ApiTestClient.PASSWORD)), "refreshToken");

        api.put(tokenAdmin, URL + "/" + clienteId + "/suspension", Map.of("suspendida", true))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suspendida").value(true));

        // El token que ya tenía deja de servir enseguida, y no puede renovarlo ni volver a entrar
        api.get(tokenCliente, "/api/establecimientos").andExpect(status().isUnauthorized());
        api.post(null, "/api/auth/refresh", Map.of("refreshToken", refresh))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
        api.post(null, "/api/auth/login", Map.of("email", "cliente@campo.com", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("CUENTA_SUSPENDIDA"));
        // Con la contraseña mal no se revela que está suspendida
        api.post(null, "/api/auth/login", Map.of("email", "cliente@campo.com", "password", "otra-clave-123"))
            .andExpect(status().isUnauthorized());

        api.put(tokenAdmin, URL + "/" + clienteId + "/suspension", Map.of("suspendida", false))
            .andExpect(jsonPath("$.suspendida").value(false));
        api.get(tokenCliente, "/api/establecimientos").andExpect(status().isOk());
    }

    @Test
    void unClienteNoPuedeCrearNiSuspenderCuentas() throws Exception {
        String tokenCliente = registrar("Estancia", "cliente@campo.com");
        String clienteId = api.leer(api.get(tokenCliente, "/api/auth/me"), "organizacionId");
        api.put(tokenCliente, URL + "/" + clienteId + "/suspension", Map.of("suspendida", true))
            .andExpect(status().isForbidden());
        api.post(tokenCliente, URL, cuenta("x@campo.com", "INICIAL")).andExpect(status().isForbidden());
        api.post(tokenCliente, URL + "/" + clienteId + "/reenviar-activacion", Map.of())
            .andExpect(status().isForbidden());
    }

    // ---------- Ayudas ----------

    private static Map<String, Object> cuenta(String email, String plan) {
        Map<String, Object> body = new HashMap<>();
        body.put("nombreOrganizacion", "Estancia San Juan");
        body.put("nombre", "Juan");
        body.put("apellido", "Pérez");
        body.put("email", email);
        body.put("planCodigo", plan);
        return body;
    }

    private String registrar(String organizacion, String email) throws Exception {
        return api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", organizacion,
            "nombre", "Test", "email", email, "password", ApiTestClient.PASSWORD)), "accessToken");
    }
}
