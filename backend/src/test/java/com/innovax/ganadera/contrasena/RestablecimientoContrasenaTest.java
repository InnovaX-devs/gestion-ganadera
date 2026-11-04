package com.innovax.ganadera.contrasena;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** "Olvidé mi contraseña": desde el login y desde el panel de administración. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestablecimientoContrasenaTest {

    private static final String OLVIDE = "/api/auth/olvide-contrasena";
    private static final String ADMIN = "/api/admin/organizaciones";
    private static final String EMAIL = "ana@campo.com";
    private static final String NUEVA = "otra-clave-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeEmailSender emails;

    private ApiTestClient api;

    @BeforeEach
    void preparar() {
        DbCleaner.limpiar(jdbc);
        emails.limpiar();
        api = new ApiTestClient(mockMvc, objectMapper);
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    @Test
    void eligeUnaContrasenaNuevaDesdeElLinkYSeCierranLasOtrasSesiones() throws Exception {
        registrar(EMAIL);
        String refreshViejo = api.leer(login(EMAIL, ApiTestClient.PASSWORD), "refreshToken");
        // Los JWT guardan la hora en segundos: el cambio tiene que caer en un segundo posterior
        Thread.sleep(1100);

        api.post(null, OLVIDE, Map.of("email", "ANA@campo.com")).andExpect(status().isNoContent());
        assertEquals(EMAIL, emails.ultimo().destinatario());
        String token = emails.ultimoToken();

        api.get(null, "/api/restablecer/" + token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.nombre").value("Ana"));
        api.post(null, "/api/restablecer/" + token, Map.of("password", "corta"))
            .andExpect(status().isBadRequest());
        api.post(null, "/api/restablecer/" + token, Map.of("password", NUEVA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // La nueva sirve, la vieja no, y la sesión de antes no se puede renovar
        login(EMAIL, NUEVA).andExpect(status().isOk());
        login(EMAIL, ApiTestClient.PASSWORD).andExpect(status().isUnauthorized());
        api.post(null, "/api/auth/refresh", Map.of("refreshToken", refreshViejo))
            .andExpect(status().isUnauthorized());
        // El link sirve una sola vez
        api.post(null, "/api/restablecer/" + token, Map.of("password", "una-mas-789"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noRevelaSiElEmailExiste() throws Exception {
        api.post(null, OLVIDE, Map.of("email", "nadie@campo.com")).andExpect(status().isNoContent());
        assertEquals(0, emails.mensajes().size());
        api.post(null, OLVIDE, Map.of("email", "no-es-email")).andExpect(status().isBadRequest());
        api.get(null, "/api/restablecer/token-inventado").andExpect(status().isNotFound());
    }

    @Test
    void soloValeElUltimoLinkYNoSeMandanDosSeguidos() throws Exception {
        registrar(EMAIL);
        api.post(null, OLVIDE, Map.of("email", EMAIL)).andExpect(status().isNoContent());
        // Un segundo pedido enseguida no manda otro email (la respuesta es la misma)
        api.post(null, OLVIDE, Map.of("email", EMAIL)).andExpect(status().isNoContent());
        assertEquals(1, emails.mensajes().size());
    }

    @Test
    void elPanelLeMandaElLinkAlDueno() throws Exception {
        String tokenAdmin = api.loginAdmin();
        String tokenAna = registrar(EMAIL);
        String orgId = api.leer(api.get(tokenAna, "/api/auth/me"), "organizacionId");

        api.post(tokenAdmin, ADMIN + "/" + orgId + "/restablecer-contrasena", Map.of())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL));
        assertEquals(EMAIL, emails.ultimo().destinatario());
        api.post(null, "/api/restablecer/" + emails.ultimoToken(), Map.of("password", NUEVA))
            .andExpect(status().isOk());
        login(EMAIL, NUEVA).andExpect(status().isOk());

        // Un cliente no puede usarlo
        api.post(tokenAna, ADMIN + "/" + orgId + "/restablecer-contrasena", Map.of())
            .andExpect(status().isForbidden());
    }

    @Test
    void conLaCuentaSinActivarElPanelPideReenviarLaActivacion() throws Exception {
        String tokenAdmin = api.loginAdmin();
        String orgId = api.leer(api.post(tokenAdmin, ADMIN, Map.of("nombreOrganizacion", "Estancia",
            "nombre", "Juan", "email", "juan@campo.com", "planCodigo", "INICIAL")), "id");

        api.post(tokenAdmin, ADMIN + "/" + orgId + "/restablecer-contrasena", Map.of())
            .andExpect(status().isUnprocessableEntity());

        // Pero si el dueño usa "olvidé mi contraseña", eso también activa la cuenta
        api.post(null, OLVIDE, Map.of("email", "juan@campo.com")).andExpect(status().isNoContent());
        api.post(null, "/api/restablecer/" + emails.ultimoToken(), Map.of("password", NUEVA))
            .andExpect(status().isOk());
        api.get(tokenAdmin, ADMIN)
            .andExpect(jsonPath("$[?(@.duenoEmail == 'juan@campo.com')].duenoPendiente", contains(false)));
    }

    // ---------- Ayudas ----------

    private String registrar(String email) throws Exception {
        return api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", "Estancia",
            "nombre", "Ana", "email", email, "password", ApiTestClient.PASSWORD)), "accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return api.post(null, "/api/auth/login", Map.of("email", email, "password", password));
    }
}
