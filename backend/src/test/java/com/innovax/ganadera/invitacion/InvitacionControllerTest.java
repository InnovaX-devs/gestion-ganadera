package com.innovax.ganadera.invitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;
import com.innovax.ganadera.support.FakeEmailSender;

/**
 * Issue #12. Cubre los dos lados: el dueño que invita y el invitado que abre el link.
 *
 * <p>Los usuarios extra de la misma organización se insertan directo en la base (todavía no hay
 * otra forma de crearlos que no sea una invitación).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitacionControllerTest {

    private static final String ESTABLECIMIENTOS = "/api/establecimientos";
    private static final String INVITACIONES = "/api/invitaciones/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FakeEmailSender emails;

    private ApiTestClient api;
    private String tokenDueno;
    private String establecimientoId;

    private record Cuenta(String email, String token) {
    }

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        emails.limpiar();
        api = new ApiTestClient(mockMvc, objectMapper);
        tokenDueno = api.registrarCuenta("Organización A");
        establecimientoId = api.leer(api.post(tokenDueno, ESTABLECIMIENTOS, Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- El dueño invita ----------

    @Test
    void elDuenoInvitaPorEmailYSeEnviaElLinkSinExponerElToken() throws Exception {
        api.post(tokenDueno, invitaciones(), Map.of("email", "  Nueva.Persona@Test.com ", "rol", "ENCARGADO"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("nueva.persona@test.com"))
            .andExpect(jsonPath("$.rol").value("ENCARGADO"))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.venceAt").exists())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.tokenHash").doesNotExist());

        assertThat(emails.mensajes()).hasSize(1);
        assertThat(emails.ultimo().destinatario()).isEqualTo("nueva.persona@test.com");
        assertThat(emails.ultimo().texto()).contains("La Esperanza").contains("Encargado").contains("/invitacion/");
    }

    @Test
    void laBaseGuardaSoloElHashDelToken() throws Exception {
        invitarYObtenerToken("nueva@test.com", Rol.SOCIO);

        String guardado = jdbc.queryForObject("SELECT token_hash FROM invitacion", String.class);
        assertThat(guardado).hasSize(64).isNotEqualTo(emails.ultimoToken());
    }

    @Test
    void validaElPedido() throws Exception {
        api.post(tokenDueno, invitaciones(), Map.of("email", "", "rol", "SOCIO")).andExpect(status().isBadRequest());
        api.post(tokenDueno, invitaciones(), Map.of("email", "no-es-un-email", "rol", "SOCIO"))
            .andExpect(status().isBadRequest());
        api.post(tokenDueno, invitaciones(), Map.of("email", "a@test.com")).andExpect(status().isBadRequest());
        api.post(tokenDueno, invitaciones(), Map.of("email", "a@test.com", "rol", "REY"))
            .andExpect(status().isBadRequest());
        assertThat(emails.mensajes()).isEmpty();
    }

    @Test
    void noSePuedeInvitarDosVecesAlMismoEmailMientrasHayaUnaPendiente() throws Exception {
        invitarYObtenerToken("nueva@test.com", Rol.SOCIO);

        api.post(tokenDueno, invitaciones(), Map.of("email", "NUEVA@test.com", "rol", "ENCARGADO"))
            .andExpect(status().isConflict());
    }

    @Test
    void noSePuedeInvitarAUnMiembroNiAUnaCuentaDeOtraOrganizacion() throws Exception {
        Cuenta miembro = nuevoUsuario(tokenDueno);
        api.post(tokenDueno, ESTABLECIMIENTOS + "/" + establecimientoId + "/miembros",
            Map.of("email", miembro.email(), "rol", "SOCIO")).andExpect(status().isCreated());
        String tokenB = api.registrarCuenta("Organización B");
        String emailDeB = api.leer(api.get(tokenB, "/api/auth/me"), "email");

        api.post(tokenDueno, invitaciones(), Map.of("email", miembro.email(), "rol", "SOCIO"))
            .andExpect(status().isConflict());
        api.post(tokenDueno, invitaciones(), Map.of("email", emailDeB, "rol", "SOCIO"))
            .andExpect(status().isConflict());
        assertThat(emails.mensajes()).isEmpty();
    }

    // ---------- Permisos: solo el dueño gestiona invitaciones ----------

    @ParameterizedTest
    @CsvSource({"DUENO,201", "ENCARGADO,403", "VETERINARIO,403", "SOCIO,403"})
    void invitar(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);

        api.post(token, invitaciones(), Map.of("email", "nueva@test.com", "rol", "SOCIO"))
            .andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,200", "ENCARGADO,403", "VETERINARIO,403", "SOCIO,403"})
    void verLasInvitaciones(Rol rol, int esperado) throws Exception {
        String token = tokenConRol(rol);

        api.get(token, invitaciones()).andExpect(status().is(esperado));
    }

    @ParameterizedTest
    @CsvSource({"DUENO,200,204", "ENCARGADO,403,403", "VETERINARIO,403,403", "SOCIO,403,403"})
    void reenviarYRevocar(Rol rol, int esperadoReenviar, int esperadoRevocar) throws Exception {
        String token = tokenConRol(rol);
        String id = invitar("nueva@test.com", Rol.SOCIO);

        api.post(token, invitaciones() + "/" + id + "/reenviar", Map.of()).andExpect(status().is(esperadoReenviar));
        api.delete(token, invitaciones() + "/" + id).andExpect(status().is(esperadoRevocar));
    }

    // ---------- El invitado sin cuenta se registra desde el link ----------

    @Test
    void elInvitadoSinCuentaVeLaInvitacionYSeRegistraEnLaOrganizacionDelDueno() throws Exception {
        String token = invitarYObtenerToken("vera@test.com", Rol.VETERINARIO);

        // El link se abre sin estar logueado
        api.get(null, INVITACIONES + token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("vera@test.com"))
            .andExpect(jsonPath("$.establecimientoNombre").value("La Esperanza"))
            .andExpect(jsonPath("$.organizacionNombre").value("Organización A"))
            .andExpect(jsonPath("$.rol").value("VETERINARIO"))
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.cuentaExistente").value(false));

        String tokenNuevo = api.leer(api.post(null, INVITACIONES + token + "/registro",
                Map.of("nombre", "Vera", "apellido", "Vet", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.usuario.email").value("vera@test.com")), "accessToken");

        // Quedó en la organización del dueño (no creó una nueva) y con el rol de la invitación
        assertThat(api.leer(api.get(tokenNuevo, "/api/auth/me"), "organizacionId"))
            .isEqualTo(api.leer(api.get(tokenDueno, "/api/auth/me"), "organizacionId"));
        api.get(tokenNuevo, ESTABLECIMIENTOS)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].nombre").value("La Esperanza"))
            .andExpect(jsonPath("$[0].rol").value("VETERINARIO"));
        api.get(tokenDueno, invitaciones()).andExpect(jsonPath("$[0].estado").value("ACEPTADA"));

        // Puede volver a ingresar con la contraseña que eligió
        api.post(null, "/api/auth/login", Map.of("email", "vera@test.com", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isOk());
        // El link ya no sirve
        api.post(null, INVITACIONES + token + "/registro", Map.of("nombre", "Otro", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elRegistroDesdeElLinkValidaLosDatos() throws Exception {
        String token = invitarYObtenerToken("vera@test.com", Rol.SOCIO);

        api.post(null, INVITACIONES + token + "/registro", Map.of("nombre", "", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isBadRequest());
        api.post(null, INVITACIONES + token + "/registro", Map.of("nombre", "Vera", "password", "corta"))
            .andExpect(status().isBadRequest());
        // Con datos válidos el link sigue funcionando: los intentos fallidos no lo gastan
        api.post(null, INVITACIONES + token + "/registro", Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isCreated());
    }

    @Test
    void unTokenInventadoDevuelve404() throws Exception {
        api.get(null, INVITACIONES + "token-inventado").andExpect(status().isNotFound());
        api.post(null, INVITACIONES + "token-inventado/registro",
            Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD)).andExpect(status().isNotFound());
    }

    // ---------- El invitado que ya tiene cuenta en la organización ----------

    @Test
    void elInvitadoConCuentaIngresaYAceptaLaInvitacion() throws Exception {
        Cuenta existente = nuevoUsuario(tokenDueno);
        String token = invitarYObtenerToken(existente.email(), Rol.ENCARGADO);

        api.get(null, INVITACIONES + token).andExpect(jsonPath("$.cuentaExistente").value(true));
        // No puede crear otra cuenta con ese email
        api.post(null, INVITACIONES + token + "/registro",
            Map.of("nombre", "Otro", "password", ApiTestClient.PASSWORD)).andExpect(status().isConflict());
        // Aceptar requiere estar logueado
        api.post(null, INVITACIONES + token + "/aceptar", Map.of()).andExpect(status().isUnauthorized());

        api.get(existente.token(), ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(0));
        api.post(existente.token(), INVITACIONES + token + "/aceptar", Map.of())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.establecimientoId").value(establecimientoId));

        api.get(existente.token(), ESTABLECIMIENTOS)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rol").value("ENCARGADO"));
        // Una invitación se acepta una sola vez
        api.post(existente.token(), INVITACIONES + token + "/aceptar", Map.of())
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nadieMasPuedeAceptarUnaInvitacionAjena() throws Exception {
        String token = invitarYObtenerToken("vera@test.com", Rol.SOCIO);
        Cuenta otro = nuevoUsuario(tokenDueno);

        api.post(otro.token(), INVITACIONES + token + "/aceptar", Map.of()).andExpect(status().isForbidden());
        // Ni siquiera el dueño que la creó
        api.post(tokenDueno, INVITACIONES + token + "/aceptar", Map.of()).andExpect(status().isForbidden());
        api.get(otro.token(), ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unaCuentaDeOtraOrganizacionNoPuedeAceptar() throws Exception {
        String token = invitarYObtenerToken("vera@test.com", Rol.SOCIO);
        // Simula que el invitado ya tenía una cuenta propia (otra organización) con ese email
        String tokenB = api.registrarCuenta("Organización B");
        String emailDeB = api.leer(api.get(tokenB, "/api/auth/me"), "email");
        jdbc.update("UPDATE invitacion SET email = ?", emailDeB);

        api.post(tokenB, INVITACIONES + token + "/aceptar", Map.of()).andExpect(status().isConflict());
        api.get(tokenB, ESTABLECIMIENTOS).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- El link vence ----------

    @Test
    void unaInvitacionVencidaNoSirveYSeReenviaConUnLinkNuevo() throws Exception {
        String id = invitar("vera@test.com", Rol.SOCIO);
        String tokenViejo = emails.ultimoToken();
        jdbc.update("UPDATE invitacion SET vence_at = ?", OffsetDateTime.now().minusDays(1));

        api.get(null, INVITACIONES + tokenViejo).andExpect(jsonPath("$.estado").value("VENCIDA"));
        api.get(tokenDueno, invitaciones()).andExpect(jsonPath("$[0].estado").value("VENCIDA"));
        api.post(null, INVITACIONES + tokenViejo + "/registro",
            Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD)).andExpect(status().isUnprocessableEntity());

        // Reenviar: link nuevo, plazo nuevo, y el link viejo deja de existir
        api.post(tokenDueno, invitaciones() + "/" + id + "/reenviar", Map.of())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        assertThat(emails.mensajes()).hasSize(2);
        String tokenNuevo = emails.ultimoToken();
        assertThat(tokenNuevo).isNotEqualTo(tokenViejo);
        api.get(null, INVITACIONES + tokenViejo).andExpect(status().isNotFound());
        api.post(null, INVITACIONES + tokenNuevo + "/registro",
            Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD)).andExpect(status().isCreated());
    }

    @Test
    void unaInvitacionVencidaPermiteInvitarDeNuevoAlMismoEmail() throws Exception {
        invitar("vera@test.com", Rol.SOCIO);
        jdbc.update("UPDATE invitacion SET vence_at = ?", OffsetDateTime.now().minusDays(1));

        api.post(tokenDueno, invitaciones(), Map.of("email", "vera@test.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
    }

    // ---------- Revocar ----------

    @Test
    void unaInvitacionRevocadaNoSirveNiSePuedeReenviar() throws Exception {
        String id = invitar("vera@test.com", Rol.SOCIO);
        String token = emails.ultimoToken();

        api.delete(tokenDueno, invitaciones() + "/" + id).andExpect(status().isNoContent());
        api.delete(tokenDueno, invitaciones() + "/" + id).andExpect(status().isNoContent());

        api.get(null, INVITACIONES + token).andExpect(jsonPath("$.estado").value("REVOCADA"));
        api.get(tokenDueno, invitaciones()).andExpect(jsonPath("$[0].estado").value("REVOCADA"));
        api.post(null, INVITACIONES + token + "/registro",
            Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD)).andExpect(status().isUnprocessableEntity());
        api.post(tokenDueno, invitaciones() + "/" + id + "/reenviar", Map.of())
            .andExpect(status().isUnprocessableEntity());
        // Revocada, se puede invitar de nuevo al mismo email
        api.post(tokenDueno, invitaciones(), Map.of("email", "vera@test.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
    }

    @Test
    void unaInvitacionAceptadaNoSePuedeRevocarNiReenviar() throws Exception {
        String id = invitar("vera@test.com", Rol.SOCIO);
        api.post(null, INVITACIONES + emails.ultimoToken() + "/registro",
            Map.of("nombre", "Vera", "password", ApiTestClient.PASSWORD)).andExpect(status().isCreated());

        api.delete(tokenDueno, invitaciones() + "/" + id).andExpect(status().isUnprocessableEntity());
        api.post(tokenDueno, invitaciones() + "/" + id + "/reenviar", Map.of())
            .andExpect(status().isUnprocessableEntity());
    }

    // ---------- Lista ----------

    @Test
    void laListaMuestraLasMasNuevasPrimero() throws Exception {
        invitar("primera@test.com", Rol.SOCIO);
        invitar("segunda@test.com", Rol.ENCARGADO);

        api.get(tokenDueno, invitaciones())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].email").value("segunda@test.com"))
            .andExpect(jsonPath("$[0].invitadoPorNombre").value("Test"))
            .andExpect(jsonPath("$[1].email").value("primera@test.com"));
    }

    // ---------- Aislamiento entre organizaciones ----------

    @Test
    void otraOrganizacionNoPuedeVerNiTocarLasInvitaciones() throws Exception {
        String id = invitar("vera@test.com", Rol.SOCIO);
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, invitaciones()).andExpect(status().isNotFound());
        api.post(tokenB, invitaciones(), Map.of("email", "x@test.com", "rol", "SOCIO"))
            .andExpect(status().isNotFound());
        api.post(tokenB, invitaciones() + "/" + id + "/reenviar", Map.of()).andExpect(status().isNotFound());
        api.delete(tokenB, invitaciones() + "/" + id).andExpect(status().isNotFound());

        api.get(tokenDueno, invitaciones())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    void unUsuarioSinMembresiaNoVeLasInvitaciones() throws Exception {
        invitar("vera@test.com", Rol.SOCIO);
        Cuenta ajeno = nuevoUsuario(tokenDueno);

        api.get(ajeno.token(), invitaciones()).andExpect(status().isNotFound());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        api.get(null, invitaciones()).andExpect(status().isUnauthorized());
        api.post(null, invitaciones(), Map.of("email", "a@test.com", "rol", "SOCIO"))
            .andExpect(status().isUnauthorized());
    }

    // ---------- Helpers ----------

    private String invitaciones() {
        return ESTABLECIMIENTOS + "/" + establecimientoId + "/invitaciones";
    }

    /** Invita como dueño y devuelve el id de la invitación. */
    private String invitar(String email, Rol rol) throws Exception {
        return api.leer(api.post(tokenDueno, invitaciones(), Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated()), "id");
    }

    /** Invita como dueño y devuelve el token del link que se mandó por email. */
    private String invitarYObtenerToken(String email, Rol rol) throws Exception {
        invitar(email, rol);
        return emails.ultimoToken();
    }

    /** Crea un usuario de la organización, lo agrega al establecimiento con ese rol y devuelve su token. */
    private String tokenConRol(Rol rol) throws Exception {
        Cuenta usuario = nuevoUsuario(tokenDueno);
        api.post(tokenDueno, ESTABLECIMIENTOS + "/" + establecimientoId + "/miembros",
            Map.of("email", usuario.email(), "rol", rol.name())).andExpect(status().isCreated());
        return usuario.token();
    }

    /** Inserta un usuario en la organización del token y lo loguea. */
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
}
