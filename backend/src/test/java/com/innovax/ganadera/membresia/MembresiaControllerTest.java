package com.innovax.ganadera.membresia;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/**
 * Issue #11. Los tests por rol son la matriz de docs/permisos.md escrita a mano (rol, código
 * esperado): si cambia la matriz, cambian acá.
 *
 * <p>Todos los usuarios de cada test son de la misma organización. Los usuarios extra se insertan
 * directo en la base porque todavía no existe una forma de invitarlos desde la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembresiaControllerTest {

    private static final String ESTABLECIMIENTOS = "/api/establecimientos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private String tokenDueno;
    private String establecimientoId;

    /** Un usuario de la organización con su email y su token. */
    private record Cuenta(String email, String token) {
    }

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenDueno = api.registrarCuenta("Organización A");
        establecimientoId = crearEstablecimiento(tokenDueno, "La Esperanza");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Alta del establecimiento ----------

    @Test
    void quienCreaUnEstablecimientoQuedaComoDueno() throws Exception {
        api.post(tokenDueno, ESTABLECIMIENTOS, Map.of("nombre", "El Ombú"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rol").value("DUENO"));

        api.get(tokenDueno, miembros(establecimientoId))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rol").value("DUENO"));
    }

    // ---------- Matriz de permisos, rol por rol ----------

    @ParameterizedTest
    @EnumSource(Rol.class)
    void todosLosRolesPuedenVer(Rol rol) throws Exception {
        String token = tokenConRol(rol);

        api.get(token, ESTABLECIMIENTOS)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rol").value(rol.name()));
        api.get(token, ESTABLECIMIENTOS + "/" + establecimientoId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rol").value(rol.name()));
        api.get(token, agrupaciones("lotes")).andExpect(status().isOk());
        api.get(token, agrupaciones("rodeos")).andExpect(status().isOk());
        api.get(token, miembros(establecimientoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,200", "ENCARGADO,200", "VETERINARIO,403", "SOCIO,403"})
    void editarElEstablecimiento(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);

        api.put(token, ESTABLECIMIENTOS + "/" + establecimientoId, Map.of("nombre", "La Nueva Esperanza"))
            .andExpect(status().is(esperado));

        // Si se rechazó, el establecimiento no cambió
        String nombreEsperado = esperado == 200 ? "La Nueva Esperanza" : "La Esperanza";
        api.get(tokenDueno, ESTABLECIMIENTOS + "/" + establecimientoId)
            .andExpect(jsonPath("$.nombre").value(nombreEsperado));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,204", "ENCARGADO,403", "VETERINARIO,403", "SOCIO,403"})
    void eliminarElEstablecimiento(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);

        api.delete(token, ESTABLECIMIENTOS + "/" + establecimientoId).andExpect(status().is(esperado));

        int seguiaVisible = esperado == 204 ? 404 : 200;
        api.get(tokenDueno, ESTABLECIMIENTOS + "/" + establecimientoId).andExpect(status().is(seguiaVisible));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,201", "ENCARGADO,201", "VETERINARIO,403", "SOCIO,403"})
    void crearLotesYRodeos(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);

        for (String tipo : List.of("lotes", "rodeos")) {
            api.post(token, agrupaciones(tipo), Map.of("nombre", "Cría")).andExpect(status().is(esperado));
        }
    }

    @ParameterizedTest
    @CsvSource({"DUENO,200,204", "ENCARGADO,200,204", "VETERINARIO,403,403", "SOCIO,403,403"})
    void editarYEliminarLotesYRodeos(Rol rol, int esperadoEditar, int esperadoEliminar) throws Exception {
        String token = tokenConRol(rol);

        for (String tipo : List.of("lotes", "rodeos")) {
            String id = api.leer(api.post(tokenDueno, agrupaciones(tipo), Map.of("nombre", "Cría"))
                .andExpect(status().isCreated()), "id");

            api.put(token, agrupaciones(tipo) + "/" + id, Map.of("nombre", "Recría"))
                .andExpect(status().is(esperadoEditar));
            api.delete(token, agrupaciones(tipo) + "/" + id).andExpect(status().is(esperadoEliminar));
        }
    }

    @ParameterizedTest
    @CsvSource({"DUENO,201", "ENCARGADO,403", "VETERINARIO,403", "SOCIO,403"})
    void agregarMiembros(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);
        Cuenta nuevo = nuevoUsuario(tokenDueno);

        api.post(token, miembros(establecimientoId), Map.of("email", nuevo.email(), "rol", "SOCIO"))
            .andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,200,204", "ENCARGADO,403,403", "VETERINARIO,403,403", "SOCIO,403,403"})
    void cambiarElRolYQuitarMiembros(Rol rol, int esperadoCambiar, int esperadoQuitar) throws Exception {
        String token = tokenConRol(rol);
        String membresiaId = agregarMiembro(tokenDueno, establecimientoId, nuevoUsuario(tokenDueno), Rol.SOCIO);
        String url = miembros(establecimientoId) + "/" + membresiaId;

        api.put(token, url, Map.of("rol", "VETERINARIO")).andExpect(status().is(esperadoCambiar));
        api.delete(token, url).andExpect(status().is(esperadoQuitar));
    }

    // ---------- Un usuario, distinto rol en cada establecimiento ----------

    @Test
    void unUsuarioPuedeTenerUnRolDistintoEnCadaEstablecimiento() throws Exception {
        String otro = crearEstablecimiento(tokenDueno, "El Ombú");
        Cuenta usuario = nuevoUsuario(tokenDueno);
        agregarMiembro(tokenDueno, establecimientoId, usuario, Rol.ENCARGADO);
        agregarMiembro(tokenDueno, otro, usuario, Rol.SOCIO);

        // Encargado en La Esperanza: puede crear lotes. Socio en El Ombú: no
        api.post(usuario.token(), "/api/establecimientos/" + establecimientoId + "/lotes", Map.of("nombre", "Cría"))
            .andExpect(status().isCreated());
        api.post(usuario.token(), "/api/establecimientos/" + otro + "/lotes", Map.of("nombre", "Cría"))
            .andExpect(status().isForbidden());

        // El listado trae cada establecimiento con su rol (ordenado por nombre)
        api.get(usuario.token(), ESTABLECIMIENTOS)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nombre").value("El Ombú"))
            .andExpect(jsonPath("$[0].rol").value("SOCIO"))
            .andExpect(jsonPath("$[1].nombre").value("La Esperanza"))
            .andExpect(jsonPath("$[1].rol").value("ENCARGADO"));
    }

    @Test
    void unUsuarioSinMembresiaNoVeNadaDelEstablecimiento() throws Exception {
        // Es de la misma organización, pero nadie lo agregó al establecimiento
        String token = nuevoUsuario(tokenDueno).token();
        String url = ESTABLECIMIENTOS + "/" + establecimientoId;

        api.get(token, ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(0));
        api.get(token, url).andExpect(status().isNotFound());
        api.put(token, url, Map.of("nombre", "Robado")).andExpect(status().isNotFound());
        api.delete(token, url).andExpect(status().isNotFound());
        api.get(token, agrupaciones("lotes")).andExpect(status().isNotFound());
        api.post(token, agrupaciones("rodeos"), Map.of("nombre", "Intruso")).andExpect(status().isNotFound());
        api.get(token, miembros(establecimientoId)).andExpect(status().isNotFound());
    }

    @Test
    void sacarleElAccesoAUnMiembroLoDejaSinVerElEstablecimiento() throws Exception {
        Cuenta usuario = nuevoUsuario(tokenDueno);
        String membresiaId = agregarMiembro(tokenDueno, establecimientoId, usuario, Rol.ENCARGADO);
        api.get(usuario.token(), ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(1));

        api.delete(tokenDueno, miembros(establecimientoId) + "/" + membresiaId).andExpect(status().isNoContent());

        api.get(usuario.token(), ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(0));
        api.get(usuario.token(), ESTABLECIMIENTOS + "/" + establecimientoId).andExpect(status().isNotFound());
    }

    // ---------- Reglas del equipo ----------

    @Test
    void noSePuedeQuitarNiDegradarAlUltimoDueno() throws Exception {
        String url = miembros(establecimientoId) + "/" + idDelPrimerMiembro(tokenDueno);

        api.put(tokenDueno, url, Map.of("rol", "ENCARGADO"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El establecimiento tiene que tener al menos un dueño"));
        api.delete(tokenDueno, url).andExpect(status().isUnprocessableEntity());

        // Con un segundo dueño, el primero ya se puede degradar
        agregarMiembro(tokenDueno, establecimientoId, nuevoUsuario(tokenDueno), Rol.DUENO);
        api.put(tokenDueno, url, Map.of("rol", "ENCARGADO")).andExpect(status().isOk());
    }

    @Test
    void agregarUnMiembroDevuelveSusDatos() throws Exception {
        Cuenta usuario = nuevoUsuario(tokenDueno);

        api.post(tokenDueno, miembros(establecimientoId), Map.of("email", usuario.email().toUpperCase(), "rol", "VETERINARIO"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(usuario.email()))
            .andExpect(jsonPath("$.nombre").value("Miembro"))
            .andExpect(jsonPath("$.rol").value("VETERINARIO"));
    }

    @Test
    void agregarUnMiembroValidaElPedido() throws Exception {
        Cuenta usuario = nuevoUsuario(tokenDueno);
        String url = miembros(establecimientoId);

        api.post(tokenDueno, url, Map.of("email", "", "rol", "SOCIO")).andExpect(status().isBadRequest());
        api.post(tokenDueno, url, Map.of("email", "no-es-un-email", "rol", "SOCIO")).andExpect(status().isBadRequest());
        api.post(tokenDueno, url, Map.of("email", usuario.email())).andExpect(status().isBadRequest());
        api.post(tokenDueno, url, Map.of("email", usuario.email(), "rol", "REY")).andExpect(status().isBadRequest());

        api.post(tokenDueno, url, Map.of("email", usuario.email(), "rol", "SOCIO")).andExpect(status().isCreated());
        api.post(tokenDueno, url, Map.of("email", usuario.email(), "rol", "ENCARGADO")).andExpect(status().isConflict());
    }

    @Test
    void soloSePuedeAgregarAUsuariosDeLaMismaOrganizacion() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        String emailDeB = api.leer(api.get(tokenB, "/api/auth/me"), "email");
        String url = miembros(establecimientoId);

        api.post(tokenDueno, url, Map.of("email", emailDeB, "rol", "SOCIO")).andExpect(status().isNotFound());
        api.post(tokenDueno, url, Map.of("email", "nadie@test.com", "rol", "SOCIO")).andExpect(status().isNotFound());
    }

    // ---------- Aislamiento entre organizaciones ----------

    @Test
    void otraOrganizacionNoPuedeVerNiTocarLosMiembros() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        String url = miembros(establecimientoId);
        String membresiaId = idDelPrimerMiembro(tokenDueno);

        api.get(tokenB, url).andExpect(status().isNotFound());
        api.post(tokenB, url, Map.of("email", "x@test.com", "rol", "SOCIO")).andExpect(status().isNotFound());
        api.put(tokenB, url + "/" + membresiaId, Map.of("rol", "SOCIO")).andExpect(status().isNotFound());
        api.delete(tokenB, url + "/" + membresiaId).andExpect(status().isNotFound());

        api.get(tokenDueno, url)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rol").value("DUENO"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, miembros(establecimientoId)).andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private static String miembros(String establecimiento) {
        return ESTABLECIMIENTOS + "/" + establecimiento + "/miembros";
    }

    private String agrupaciones(String tipo) {
        return ESTABLECIMIENTOS + "/" + establecimientoId + "/" + tipo;
    }

    private String crearEstablecimiento(String token, String nombre) throws Exception {
        return api.leer(api.post(token, ESTABLECIMIENTOS, Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    /** Crea un usuario de la organización, lo agrega al establecimiento con ese rol y devuelve su token. */
    private String tokenConRol(Rol rol) throws Exception {
        Cuenta usuario = nuevoUsuario(tokenDueno);
        agregarMiembro(tokenDueno, establecimientoId, usuario, rol);
        return usuario.token();
    }

    /** Agrega al establecimiento a alguien de la organización y devuelve el id de la membresía. */
    private String agregarMiembro(String token, String establecimiento, Cuenta usuario, Rol rol) throws Exception {
        ResultActions resultado = api.post(token, miembros(establecimiento),
            Map.of("email", usuario.email(), "rol", rol.name()));
        return api.leer(resultado.andExpect(status().isCreated()), "id");
    }

    /** Inserta un usuario en la organización del token (no hay otra forma de crearlo) y lo loguea. */
    private Cuenta nuevoUsuario(String tokenDeLaOrganizacion) throws Exception {
        String organizacionId = api.leer(api.get(tokenDeLaOrganizacion, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        String token = api.leer(api.post(null, "/api/auth/login",
            Map.of("email", email, "password", ApiTestClient.PASSWORD)), "accessToken");
        return new Cuenta(email, token);
    }

    private String idDelPrimerMiembro(String token) throws Exception {
        String json = api.get(token, miembros(establecimientoId)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get(0).get("id").asText();
    }
}
