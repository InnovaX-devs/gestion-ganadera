package com.innovax.ganadera.support;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Atajos para tests de controllers: registrar cuentas reales y hacer requests autenticados.
 *
 * <pre>
 * var api = new ApiTestClient(mockMvc, objectMapper);
 * String token = api.registrarCuenta("Estancia A");
 * api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza")).andExpect(status().isCreated());
 * </pre>
 */
public class ApiTestClient {

    public static final String PASSWORD = "clave-segura-123";
    /** Panel de administración: app.admin.* de application-test.yml */
    public static final String ADMIN_EMAIL = "admin@innovax.test";
    public static final String ADMIN_PASSWORD = "clave-admin-de-prueba";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Crea una organización nueva con su usuario y devuelve el access token. */
    public String registrarCuenta(String nombreOrganizacion) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        ResultActions result = mockMvc.perform(MockMvcRequestBuildersAlias.post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "nombreOrganizacion", nombreOrganizacion,
                "nombre", "Test",
                "email", email,
                "password", PASSWORD))));
        return leer(result, "accessToken");
    }

    /** Entra al panel de administración y devuelve su token (rol ADMIN). */
    public String loginAdmin() throws Exception {
        return leer(post(null, "/api/admin/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)),
            "accessToken");
    }

    public ResultActions get(String token, String url) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuildersAlias.get(url), token));
    }

    public ResultActions post(String token, String url, Object body) throws Exception {
        return mockMvc.perform(conJson(conToken(MockMvcRequestBuildersAlias.post(url), token), body));
    }

    /** Sube un archivo como multipart/form-data (parte "archivo") con parámetros opcionales, ej: cargaId. */
    public ResultActions postArchivo(String token, String url, MockMultipartFile archivo, Map<String, String> params)
        throws Exception {
        var request = MockMvcRequestBuildersAlias.multipart(url).file(archivo);
        params.forEach(request::param);
        return mockMvc.perform(conToken(request, token));
    }

    public ResultActions put(String token, String url, Object body) throws Exception {
        return mockMvc.perform(conJson(conToken(MockMvcRequestBuildersAlias.put(url), token), body));
    }

    public ResultActions delete(String token, String url) throws Exception {
        return mockMvc.perform(conToken(MockMvcRequestBuildersAlias.delete(url), token));
    }

    /** Lee un campo de la respuesta JSON, ej: leer(result, "id"). */
    public String leer(ResultActions result, String campo) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get(campo).asText();
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder request, Object body)
        throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    /** Evita el choque de nombres entre los métodos de esta clase y los de MockMvcRequestBuilders. */
    private static final class MockMvcRequestBuildersAlias {
        static MockHttpServletRequestBuilder get(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        }

        static MockHttpServletRequestBuilder post(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
        }

        static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipart(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url);
        }

        static MockHttpServletRequestBuilder put(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url);
        }

        static MockHttpServletRequestBuilder delete(String url) {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url);
        }
    }
}
