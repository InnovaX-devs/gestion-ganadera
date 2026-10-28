package com.innovax.ganadera.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String EMAIL = "juan@campo.com";
    private static final String PASSWORD = "clave-segura-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM usuario");
        jdbc.update("DELETE FROM organizacion");
    }

    // ---------- Registro ----------

    @Test
    void registroCreaOrganizacionYUsuarioYDevuelveTokens() throws Exception {
        registrar(EMAIL)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.usuario.email").value(EMAIL))
            .andExpect(jsonPath("$.usuario.organizacionNombre").value("Estancia La Prueba"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizacion", Integer.class)).isEqualTo(1);
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario", String.class);
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void registroConEmailRepetidoDevuelve409() throws Exception {
        registrar(EMAIL).andExpect(status().isCreated());

        registrar("  JUAN@campo.com ")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya existe una cuenta con ese email"));
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConErroresPorCampo() throws Exception {
        var body = Map.of("nombreOrganizacion", "", "nombre", "Juan", "email", "no-es-email", "password", "123");

        postJson("/api/auth/register", body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    // ---------- Login ----------

    @Test
    void loginCorrectoDevuelveTokens() throws Exception {
        registrar(EMAIL);

        postJson("/api/auth/login", Map.of("email", "Juan@Campo.com", "password", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.usuario.nombre").value("Juan"));
    }

    @Test
    void loginConContrasenaIncorrectaOEmailInexistenteDevuelveElMismo401() throws Exception {
        registrar(EMAIL);

        postJson("/api/auth/login", Map.of("email", EMAIL, "password", "otra-clave"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(AuthService.CREDENCIALES_INVALIDAS));

        postJson("/api/auth/login", Map.of("email", "nadie@campo.com", "password", PASSWORD))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(AuthService.CREDENCIALES_INVALIDAS));
    }

    @Test
    void usuarioInactivoNoPuedeLoguearse() throws Exception {
        registrar(EMAIL);
        jdbc.update("UPDATE usuario SET activo = FALSE");

        postJson("/api/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
            .andExpect(status().isUnauthorized());
    }

    // ---------- Endpoints protegidos ----------

    @Test
    void meDevuelveElUsuarioLogueado() throws Exception {
        String accessToken = registrarYObtener("accessToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.organizacionNombre").value("Estancia La Prueba"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInventadoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer esto.no.sirve"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void elRefreshTokenNoSirveComoAccessToken() throws Exception {
        String refreshToken = registrarYObtener("refreshToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoDevuelve401() throws Exception {
        registrar(EMAIL);
        String vencido = firmarToken(TokenService.TIPO_ACCESS, Instant.now().minusSeconds(600));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + vencido))
            .andExpect(status().isUnauthorized());
    }

    // ---------- Refresh ----------

    @Test
    void refreshDevuelveTokensNuevos() throws Exception {
        String refreshToken = registrarYObtener("refreshToken");

        String nuevoAccess = leer(postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken))
            .andExpect(status().isOk()), "accessToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + nuevoAccess))
            .andExpect(status().isOk());
    }

    @Test
    void refreshConAccessTokenDevuelve401() throws Exception {
        String accessToken = registrarYObtener("accessToken");

        postJson("/api/auth/refresh", Map.of("refreshToken", accessToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(AuthService.SESION_VENCIDA));
    }

    @Test
    void refreshDeUsuarioDesactivadoDevuelve401() throws Exception {
        String refreshToken = registrarYObtener("refreshToken");
        jdbc.update("UPDATE usuario SET activo = FALSE");

        postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken))
            .andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private ResultActions registrar(String email) throws Exception {
        return postJson("/api/auth/register", Map.of(
            "nombreOrganizacion", "Estancia La Prueba",
            "nombre", "Juan",
            "apellido", "Pérez",
            "email", email,
            "password", PASSWORD));
    }

    private String registrarYObtener(String campo) throws Exception {
        return leer(registrar(EMAIL).andExpect(status().isCreated()), campo);
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private String leer(ResultActions result, String campo) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get(campo).asText();
    }

    private String firmarToken(String tipo, Instant vence) {
        UUID usuarioId = jdbc.queryForObject("SELECT id FROM usuario", UUID.class);
        UUID organizacionId = jdbc.queryForObject("SELECT organizacion_id FROM usuario", UUID.class);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(usuarioId.toString())
            .issuedAt(vence.minusSeconds(900))
            .expiresAt(vence)
            .claim("type", tipo)
            .claim("org", organizacionId.toString())
            .claim("email", EMAIL)
            .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
}
