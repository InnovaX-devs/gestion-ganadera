package com.innovax.ganadera.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.adjunto.AdjuntosProperties;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Eliminar una cuenta desde el panel: se borra todo, se puede volver a usar el email y no toca otras cuentas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EliminarCuentaTest {

    private static final String URL = "/api/admin/organizaciones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdjuntosProperties adjuntos;

    private ApiTestClient api;
    private String tokenAdmin;
    private String tokenCliente;
    private String clienteId;
    private String tokenOtra;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenAdmin = api.loginAdmin();
        tokenCliente = registrar("Estancia La Esperanza", "dueno@campo.com");
        clienteId = api.leer(api.get(tokenCliente, "/api/auth/me"), "organizacionId");
        cargarDatos(tokenCliente);
        tokenOtra = registrar("Otra", "otra@campo.com");
        cargarDatos(tokenOtra);
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    @Test
    void borraLaCuentaConTodoYElEmailSePuedeVolverAUsar() throws Exception {
        String fotoDelCliente = jdbc.queryForObject("SELECT clave FROM adjunto WHERE organizacion_id = ?",
            String.class, UUID.fromString(clienteId));
        Path archivo = Path.of(adjuntos.directorio()).toAbsolutePath().normalize().resolve(fotoDelCliente);
        assertTrue(Files.exists(archivo));

        api.post(tokenAdmin, URL + "/" + clienteId + "/eliminar", Map.of("confirmacion", "  estancia la ESPERANZA "))
            .andExpect(status().isNoContent());

        // También sus fotos, en la base y en el almacén
        assertFalse(Files.exists(archivo));
        assertEquals(1, contar("adjunto"));

        assertEquals(0, contar("organizacion WHERE id = '" + clienteId + "'"));
        assertEquals(0, contar("usuario WHERE email = 'dueno@campo.com'"));
        // La otra cuenta quedó intacta
        assertEquals(1, contar("animal"));
        assertEquals(1, contar("evento"));
        api.get(tokenOtra, "/api/establecimientos").andExpect(status().isOk());

        // La sesión que tenía deja de servir y el email está libre
        api.get(tokenCliente, "/api/establecimientos").andExpect(status().isUnauthorized());
        registrar("Estancia Nueva", "dueno@campo.com");
        api.get(tokenAdmin, URL).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void pideEscribirElNombreParaConfirmar() throws Exception {
        api.post(tokenAdmin, URL + "/" + clienteId + "/eliminar", Map.of("confirmacion", "Otra cosa"))
            .andExpect(status().isUnprocessableEntity());
        api.post(tokenAdmin, URL + "/" + UUID.randomUUID() + "/eliminar", Map.of("confirmacion", "x"))
            .andExpect(status().isNotFound());
        assertEquals(1, contar("organizacion WHERE id = '" + clienteId + "'"));
    }

    @Test
    void conUnaSuscripcionActivaHayQueCancelarlaAntes() throws Exception {
        jdbc.update("INSERT INTO suscripcion (id, organizacion_id, plan_codigo, monto, estado) VALUES (?, ?, 'INICIAL', 100, 'ACTIVA')",
            UUID.randomUUID(), UUID.fromString(clienteId));
        api.post(tokenAdmin, URL + "/" + clienteId + "/eliminar", Map.of("confirmacion", "Estancia La Esperanza"))
            .andExpect(status().isUnprocessableEntity());
        assertEquals(1, contar("organizacion WHERE id = '" + clienteId + "'"));
    }

    @Test
    void unClienteNoPuedeEliminarCuentas() throws Exception {
        api.post(tokenCliente, URL + "/" + clienteId + "/eliminar", Map.of("confirmacion", "Estancia La Esperanza"))
            .andExpect(status().isForbidden());
    }

    // ---------- Ayudas ----------

    private void cargarDatos(String token) throws Exception {
        String est = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "Campo")), "id");
        String animal = api.leer(api.post(token, "/api/establecimientos/" + est + "/animales",
            Map.of("caravana", "A-1", "sexo", "HEMBRA", "categoria", "VACA")).andExpect(status().isCreated()), "id");
        // Una foto de la vaca: un JPG mínimo (lo que importa es la firma del principio)
        byte[] foto = new byte[100];
        foto[0] = (byte) 0xFF;
        foto[1] = (byte) 0xD8;
        foto[2] = (byte) 0xFF;
        foto[3] = (byte) 0xE0;
        api.postArchivo(token, "/api/establecimientos/" + est + "/animales/" + animal + "/adjuntos",
            new MockMultipartFile("archivo", "vaca.jpg", "image/jpeg", foto), Map.of())
            .andExpect(status().isCreated());
        api.post(token, "/api/establecimientos/" + est + "/eventos", Map.of("tipo", "OBSERVACION",
            "fecha", LocalDate.now().toString(), "datos", Map.of("texto", "Se cortó el agua")))
            .andExpect(status().isCreated());
    }

    private int contar(String desde) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + desde, Integer.class);
        return n == null ? 0 : n;
    }

    private String registrar(String organizacion, String email) throws Exception {
        return api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", organizacion,
            "nombre", "Test", "email", email, "password", ApiTestClient.PASSWORD)), "accessToken");
    }
}
