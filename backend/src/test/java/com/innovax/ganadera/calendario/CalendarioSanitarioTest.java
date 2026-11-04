package com.innovax.ganadera.calendario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;
import com.innovax.ganadera.support.FakeEmailSender;

/** Issue #34: plan sanitario, alertas y aviso diario por email. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarioSanitarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** El de todos los tests: guarda los emails en vez de mandarlos. */
    @Autowired
    private FakeEmailSender emails;

    @Autowired
    private AlertasJob job;

    @Autowired
    private Clock reloj;

    /** El "hoy" del servidor (Argentina): no depende de la zona horaria de la máquina que corre el test. */
    private LocalDate hoy;
    private ApiTestClient api;
    private String token;
    private String est;
    private String lote;
    private String rodeo;
    private String ivomec;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        emails.limpiar();
        hoy = LocalDate.now(reloj);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        est = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
        lote = crear("lotes", Map.of("nombre", "Recría"));
        rodeo = crear("rodeos", Map.of("nombre", "Cría"));
        ivomec = crear("productos-sanitarios", Map.of("nombre", "Ivomec", "tipo", "ANTIPARASITARIO", "diasCarencia", 28));
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Plan sanitario ----------

    @Test
    void planificaPorLoteConRepeticionesYLoListaPorPeriodo() throws Exception {
        api.post(token, plan(), cuerpo(hoy.plusDays(5), Map.of("loteId", lote, "productoId", ivomec,
                "descripcion", "Antiparasitario", "dosis", "5 ml", "repeticiones", 3, "cadaDias", 30)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[1].fecha").value(hoy.plusDays(35).toString()))
            .andExpect(jsonPath("$[2].fecha").value(hoy.plusDays(65).toString()));

        listar(hoy, hoy.plusDays(40))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].destino.tipo").value("LOTE"))
            .andExpect(jsonPath("$[0].destino.nombre").value("Recría"))
            .andExpect(jsonPath("$[0].producto.nombre").value("Ivomec"))
            .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
            .andExpect(jsonPath("$[0].vencido").value(false))
            .andExpect(jsonPath("$[0].avisarDiasAntes").value(1))
            .andExpect(jsonPath("$[0].creadoPor").exists());
    }

    @Test
    void elDestinoPuedeSerUnRodeoOTodoElEstablecimientoPeroNoLosDos() throws Exception {
        api.post(token, plan(), cuerpo(hoy, Map.of("rodeoId", rodeo, "descripcion", "Aftosa")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].destino.tipo").value("RODEO"));
        api.post(token, plan(), cuerpo(hoy, Map.of("descripcion", "Revisar aguadas")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].destino.tipo").value("ESTABLECIMIENTO"));
        api.post(token, plan(), cuerpo(hoy, Map.of("loteId", lote, "rodeoId", rodeo, "descripcion", "X")))
            .andExpect(status().isBadRequest());
        api.post(token, plan(), cuerpo(hoy, Map.of("descripcion", "X", "repeticiones", 3)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("cadaDias"));
        api.post(token, plan(), cuerpo(hoy, Map.of("loteId", UUID.randomUUID().toString(), "descripcion", "X")))
            .andExpect(status().isNotFound());
        api.post(token, plan(), cuerpo(hoy, Map.of("descripcion", " "))).andExpect(status().isBadRequest());
    }

    @Test
    void seMarcaComoHechoSeReabreSeCancelaYSeBorra() throws Exception {
        String id = crearPlan(hoy.minusDays(2), lote, "Vacuna aftosa");
        listar(hoy.minusDays(5), hoy).andExpect(jsonPath("$[0].vencido").value(true));

        String operacion = UUID.randomUUID().toString();
        api.post(token, plan() + "/" + id + "/realizado", Map.of("operacionId", operacion))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("REALIZADO"))
            .andExpect(jsonPath("$.vencido").value(false))
            .andExpect(jsonPath("$.realizadoOperacionId").value(operacion))
            .andExpect(jsonPath("$.realizadoEn").exists());
        // Hecho no se edita ni se cancela
        api.put(token, plan() + "/" + id, cuerpo(hoy, Map.of("descripcion", "Otra")))
            .andExpect(status().isUnprocessableEntity());
        api.post(token, plan() + "/" + id + "/cancelar", Map.of()).andExpect(status().isUnprocessableEntity());

        api.post(token, plan() + "/" + id + "/reabrir", Map.of())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.realizadoOperacionId").doesNotExist());
        api.put(token, plan() + "/" + id, cuerpo(hoy.plusDays(1), Map.of("rodeoId", rodeo, "descripcion", "Aftosa 2")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.destino.tipo").value("RODEO"))
            .andExpect(jsonPath("$.descripcion").value("Aftosa 2"));
        api.post(token, plan() + "/" + id + "/cancelar", Map.of())
            .andExpect(jsonPath("$.estado").value("CANCELADO"));
        api.delete(token, plan() + "/" + id).andExpect(status().isNoContent());
        listar(hoy.minusDays(5), hoy.plusDays(5)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unSocioVePeroNoPlanificaYOtraOrganizacionNoVe() throws Exception {
        crearPlan(hoy, lote, "Aftosa");
        String socio = tokenConRol(Rol.SOCIO);

        api.get(socio, plan()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.post(socio, plan(), cuerpo(hoy, Map.of("descripcion", "X"))).andExpect(status().isForbidden());
        api.get(socio, "/api/establecimientos/" + est + "/alertas").andExpect(status().isOk());

        String tokenB = api.registrarCuenta("Organización B");
        api.get(tokenB, plan()).andExpect(status().isNotFound());
        api.get(tokenB, "/api/establecimientos/" + est + "/alertas").andExpect(status().isNotFound());
    }

    // ---------- Alertas ----------

    @Test
    void alertasDeTratamientosVencidosProximosYCarencias() throws Exception {
        crearPlan(hoy.minusDays(3), lote, "Vacuna aftosa");
        crearPlan(hoy.plusDays(2), null, "Antiparasitario");
        crearPlan(hoy.plusDays(20), null, "Lejano");
        String va = crearAnimal("V-1");
        String vb = crearAnimal("V-2");
        // V-1 queda libre en 3 días; V-2 en 26
        tratar(va, hoy.minusDays(2), 5);
        tratar(vb, hoy.minusDays(2), 28);

        api.get(token, "/api/establecimientos/" + est + "/alertas")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hoy").value(hoy.toString()))
            .andExpect(jsonPath("$.diasProximos").value(7))
            .andExpect(jsonPath("$.tratamientosVencidos.length()").value(1))
            .andExpect(jsonPath("$.tratamientosVencidos[0].descripcion").value("Vacuna aftosa"))
            .andExpect(jsonPath("$.tratamientosVencidos[0].vencido").value(true))
            .andExpect(jsonPath("$.tratamientosProximos.length()").value(1))
            .andExpect(jsonPath("$.tratamientosProximos[0].descripcion").value("Antiparasitario"))
            .andExpect(jsonPath("$.carencias.animalesEnCarencia").value(2))
            .andExpect(jsonPath("$.carencias.salenProximamente.length()").value(1))
            .andExpect(jsonPath("$.carencias.salenProximamente[0].caravana").value("V-1"))
            .andExpect(jsonPath("$.carencias.salenProximamente[0].libreDesde").value(hoy.plusDays(3).toString()))
            .andExpect(jsonPath("$.carencias.salenProximamente[0].productos").value("Ivomec"));
    }

    // ---------- Aviso por email ----------

    @Test
    void mandaElAvisoDelDiaALosQuePuedenRegistrarSanidadUnaSolaVez() throws Exception {
        crearPlan(hoy.plusDays(1), lote, "Vacuna aftosa");
        String veterinario = tokenConRol(Rol.VETERINARIO);
        tokenConRol(Rol.SOCIO);

        assertThat(job.ejecutar(hoy)).isEqualTo(2);
        assertThat(emails.mensajes()).extracting(FakeEmailSender.Mensaje::destinatario)
            .noneMatch(p -> p.contains("socio"));
        FakeEmailSender.Mensaje email = emails.mensajes().get(0);
        assertThat(email.asunto()).contains("La Esperanza");
        assertThat(email.texto())
            .contains("Próximos tratamientos")
            .contains("Vacuna aftosa · Lote Recría")
            .contains("(en 1 día)")
            .contains("/calendario-sanitario");

        // Si el job corre de nuevo el mismo día, no se repite
        assertThat(job.ejecutar(hoy)).isZero();
        assertThat(veterinario).isNotBlank();
    }

    @Test
    void sinNadaQueAvisarHoyNoMandaNada() throws Exception {
        // Faltan 10 días y el aviso es 1 día antes
        crearPlan(hoy.plusDays(10), lote, "Vacuna aftosa");

        assertThat(job.ejecutar(hoy)).isZero();
        assertThat(emails.mensajes()).isEmpty();
    }

    @Test
    void avisaCuandoUnAnimalSaleDeCarenciaManana() throws Exception {
        String vaca = crearAnimal("V-1");
        // 5 días de carencia: queda libre mañana
        tratar(vaca, hoy.minusDays(4), 5);

        assertThat(job.ejecutar(hoy)).isEqualTo(1);
        assertThat(emails.mensajes().get(0).texto()).contains("terminan la carencia").contains("V-1 (Ivomec)");
    }

    @Test
    void quienDesactivaLosEmailsNoLosRecibe() throws Exception {
        api.get(token, "/api/usuario/preferencias").andExpect(jsonPath("$.alertasEmail").value(true));
        api.put(token, "/api/usuario/preferencias", Map.of("alertasEmail", false))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alertasEmail").value(false));
        api.get(token, "/api/usuario/preferencias").andExpect(jsonPath("$.alertasEmail").value(false));
        crearPlan(hoy, lote, "Vacuna aftosa");

        assertThat(job.ejecutar(hoy)).isZero();
    }

    // ---------- Helpers ----------

    private String plan() {
        return "/api/establecimientos/" + est + "/plan-sanitario";
    }

    private ResultActions listar(LocalDate desde, LocalDate hasta) throws Exception {
        return api.get(token, plan() + "?desde=" + desde + "&hasta=" + hasta).andExpect(status().isOk());
    }

    private static Map<String, Object> cuerpo(LocalDate fecha, Map<String, Object> resto) {
        Map<String, Object> body = new HashMap<>(resto);
        body.put("fecha", fecha.toString());
        return body;
    }

    private String crearPlan(LocalDate fecha, String loteId, String descripcion) throws Exception {
        Map<String, Object> resto = new HashMap<>(Map.of("descripcion", descripcion));
        if (loteId != null) resto.put("loteId", loteId);
        String respuesta = api.post(token, plan(), cuerpo(fecha, resto))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get(0).get("id").asText();
    }

    private void tratar(String animal, LocalDate fecha, int diasCarencia) throws Exception {
        api.post(token, "/api/establecimientos/" + est + "/eventos", Map.of(
                "tipo", "TRATAMIENTO", "fecha", fecha.toString(), "animalIds", List.of(animal),
                "datos", Map.of("productoId", ivomec, "dosis", "5 ml", "via", "SUBCUTANEA",
                    "diasCarencia", diasCarencia)))
            .andExpect(status().isCreated());
    }

    private String crearAnimal(String caravana) throws Exception {
        return crear("animales", Map.of("caravana", caravana, "sexo", "HEMBRA", "categoria", "VACA"));
    }

    private String crear(String recurso, Map<String, Object> body) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos/" + est + "/" + recurso, body)
            .andExpect(status().isCreated()), "id");
    }

    /** Usuario nuevo de la organización con ese rol; su email dice el rol ("socio-...@test.com"). */
    private String tokenConRol(Rol rol) throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = rol.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, "/api/establecimientos/" + est + "/miembros", Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
