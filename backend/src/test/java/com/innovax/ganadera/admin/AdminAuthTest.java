package com.innovax.ganadera.admin;

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

/**
 * Login del panel de administración (#35): email de app.admin.emails + app.admin.password, separado de
 * las cuentas de los clientes. Su token solo sirve para /api/admin/**.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthTest {

    private static final String LOGIN = "/api/admin/auth/login";
    private static final String PANEL = "/api/admin/organizaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IntentosLoginAdmin intentos;

    private ApiTestClient api;

    @BeforeEach
    void preparar() {
        DbCleaner.limpiar(jdbc);
        intentos.reiniciar();
        api = new ApiTestClient(mockMvc, objectMapper);
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
        intentos.reiniciar();
    }

    @Test
    void entraConElEmailYLaContrasenaDelPanel() throws Exception {
        String token = api.leer(api.post(null, LOGIN, login("Admin@Innovax.test", ApiTestClient.ADMIN_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("admin@innovax.test"))
            .andExpect(jsonPath("$.expiresIn").value(8 * 3600)), "accessToken");

        api.get(token, "/api/admin/auth/me")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("admin@innovax.test"));
        api.get(token, PANEL).andExpect(status().isOk());
    }

    @Test
    void rechazaContrasenaOEmailIncorrectos() throws Exception {
        api.post(null, LOGIN, login(ApiTestClient.ADMIN_EMAIL, "otra-clave"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(AdminAuthService.CREDENCIALES_INVALIDAS));
        // La contraseña buena con un email que no es del equipo tampoco entra
        api.post(null, LOGIN, login("cualquiera@campo.com", ApiTestClient.ADMIN_PASSWORD))
            .andExpect(status().isUnauthorized());
        api.post(null, LOGIN, login("", "")).andExpect(status().isBadRequest());
        api.get(null, "/api/admin/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void despuesDeVariosIntentosFallidosSeFrena() throws Exception {
        for (int i = 0; i < IntentosLoginAdmin.MAXIMO; i++) {
            api.post(null, LOGIN, login(ApiTestClient.ADMIN_EMAIL, "intento-" + i))
                .andExpect(status().isUnauthorized());
        }
        // Ni con la contraseña correcta, hasta que pase el bloqueo
        api.post(null, LOGIN, login(ApiTestClient.ADMIN_EMAIL, ApiTestClient.ADMIN_PASSWORD))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void elTokenDelPanelNoSirveParaLaApp() throws Exception {
        String token = api.loginAdmin();
        api.get(token, "/api/auth/me").andExpect(status().isForbidden());
        api.get(token, "/api/establecimientos").andExpect(status().isForbidden());
        api.get(token, "/api/plan").andExpect(status().isForbidden());
    }

    @Test
    void unaCuentaDeLaAppNoEntraAlPanelAunqueTengaElEmailDelEquipo() throws Exception {
        String tokenUsuario = api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", "Innovax",
            "nombre", "Test", "email", ApiTestClient.ADMIN_EMAIL, "password", ApiTestClient.PASSWORD)), "accessToken");
        api.get(tokenUsuario, PANEL).andExpect(status().isForbidden());
        api.get(tokenUsuario, "/api/admin/auth/me").andExpect(status().isForbidden());
        // Y la contraseña de su cuenta no es la del panel
        api.post(null, LOGIN, login(ApiTestClient.ADMIN_EMAIL, ApiTestClient.PASSWORD))
            .andExpect(status().isUnauthorized());
    }

    private static Map<String, String> login(String email, String password) {
        return Map.of("email", email, "password", password);
    }
}
