package com.innovax.ganadera.evento;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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

/** Issue #17 (modelo genérico) probado con el tipo Observación (#24). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventoControllerTest {

    private static final String HOY = LocalDate.now().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiTestClient api;
    private String token;
    private String establecimientoId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        establecimientoId = crearEstablecimiento(token, "La Esperanza");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Registro ----------

    @Test
    void registraUnaObservacionDeUnAnimalYApareceEnSuHistorial() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), observacion(HOY, "  Rengueando de la pata trasera ", List.of(vaca)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.operacionId").isNotEmpty())
            .andExpect(jsonPath("$.eventos[0].tipo").value("OBSERVACION"))
            .andExpect(jsonPath("$.eventos[0].animal.nombre").value("V-1"))
            .andExpect(jsonPath("$.eventos[0].datos.texto").value("Rengueando de la pata trasera"))
            .andExpect(jsonPath("$.eventos[0].registradoPor.nombre").value("Test"));

        api.get(token, eventos() + "?animalId=" + vaca)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].datos.texto").value("Rengueando de la pata trasera"));
    }

    @Test
    void unEventoGrupalCreaUnoPorAnimalConLaMismaOperacion() throws Exception {
        String a1 = crearAnimal("V-1", "HEMBRA", "VACA");
        String a2 = crearAnimal("V-2", "HEMBRA", "VACA");
        String a3 = crearAnimal("V-3", "HEMBRA", "VACA");

        String operacion = api.leer(api.post(token, eventos(), observacion(HOY, "Revisión general", List.of(a1, a2, a3)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(3)), "operacionId");

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.contenido[0].operacionId").value(operacion))
            .andExpect(jsonPath("$.contenido[2].operacionId").value(operacion));
        api.get(token, eventos() + "?animalId=" + a2).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void puedeSerDelEstablecimientoODeUnLote() throws Exception {
        String loteId = api.leer(api.post(token, url("lotes"), Map.of("nombre", "Recría")), "id");

        api.post(token, eventos(), observacion(HOY, "Se cortó el agua del molino", List.of()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].animal").doesNotExist())
            .andExpect(jsonPath("$.eventos[0].lote").doesNotExist());

        var deLote = observacion(HOY, "Pasto escaso", null);
        deLote.put("loteId", loteId);
        api.post(token, eventos(), deLote)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].lote.nombre").value("Recría"));

        api.get(token, eventos() + "?loteId=" + loteId).andExpect(jsonPath("$.total").value(1));
        api.get(token, eventos() + "?tipo=OBSERVACION").andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void reenviarLaMismaOperacionNoDuplica() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String operacion = UUID.randomUUID().toString();
        var body = observacion(HOY, "Cargada sin señal", List.of(vaca));
        body.put("operacionId", operacion);

        api.post(token, eventos(), body).andExpect(status().isCreated());
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.operacionId").value(operacion))
            .andExpect(jsonPath("$.cantidad").value(1));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listaLoMasRecientePrimeroYFiltraPorFechas() throws Exception {
        api.post(token, eventos(), observacion("2026-01-10", "Enero", List.of()));
        api.post(token, eventos(), observacion("2026-06-10", "Junio", List.of()));
        api.post(token, eventos(), observacion("2026-03-10", "Marzo", List.of()));

        api.get(token, eventos())
            .andExpect(jsonPath("$.contenido[0].datos.texto").value("Junio"))
            .andExpect(jsonPath("$.contenido[2].datos.texto").value("Enero"));
        api.get(token, eventos() + "?desde=2026-02-01&hasta=2026-04-30")
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].datos.texto").value("Marzo"));
    }

    // ---------- Validaciones ----------

    @Test
    void datosDelTipoSeValidanConSuNombreDeCampo() throws Exception {
        api.post(token, eventos(), observacion(HOY, "   ", List.of()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.texto"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Escribí la observación"));
    }

    @Test
    void fechaFuturaDevuelve400YAnteriorAlNacimiento422() throws Exception {
        api.post(token, eventos(), observacion(LocalDate.now().plusDays(1).toString(), "X", List.of()))
            .andExpect(status().isBadRequest());

        var ternera = animal("T-1", "HEMBRA", "TERNERA");
        ternera.put("fechaNacimiento", "2026-05-01");
        String id = api.leer(api.post(token, url("animales"), ternera), "id");
        api.post(token, eventos(), observacion("2026-04-01", "Antes de nacer", List.of(id)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La fecha es anterior al nacimiento de T-1"));
    }

    @Test
    void tipoInexistenteDevuelve400YTipoTodaviaNoDisponible422() throws Exception {
        var invalido = observacion(HOY, "X", List.of());
        invalido.put("tipo", "INVENTADO");
        api.post(token, eventos(), invalido).andExpect(status().isBadRequest());

        // PESAJE ya existe como tipo, pero su handler llega en el issue #21
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        var pesaje = observacion(HOY, "X", List.of(vaca));
        pesaje.put("tipo", "PESAJE");
        api.post(token, eventos(), pesaje)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El evento Pesaje todavía no está disponible"));
    }

    @Test
    void animalesDeOtroEstablecimientoOAnimalesYLoteJuntosDevuelven422() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String otro = crearEstablecimiento(token, "El Ombú");
        String ajeno = api.leer(api.post(token, url(otro, "animales"), animal("Z-1", "HEMBRA", "VACA")), "id");
        String loteId = api.leer(api.post(token, url("lotes"), Map.of("nombre", "Recría")), "id");

        api.post(token, eventos(), observacion(HOY, "X", List.of(vaca, ajeno)))
            .andExpect(status().isUnprocessableEntity());

        var ambos = observacion(HOY, "X", List.of(vaca));
        ambos.put("loteId", loteId);
        api.post(token, eventos(), ambos)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá animales o un lote, no las dos cosas"));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    // ---------- Anulación ----------

    @Test
    void anularLoSacaDelHistorial() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String eventoId = idDelPrimerEvento(api.post(token, eventos(), observacion(HOY, "Error", List.of(vaca))));

        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
        api.get(token, eventos() + "/" + eventoId).andExpect(status().isNotFound());
        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNotFound());
    }

    @Test
    void unAnimalConEventosNoSePuedeBorrarHastaAnularlos() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String eventoId = idDelPrimerEvento(api.post(token, eventos(), observacion(HOY, "Nota", List.of(vaca))));

        api.delete(token, url("animales") + "/" + vaca)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("No se puede eliminar: tiene eventos registrados. Anulalos primero."));

        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());
        api.delete(token, url("animales") + "/" + vaca).andExpect(status().isNoContent());
    }

    // ---------- Permisos y aislamiento ----------

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 201", "SOCIO, 403"})
    void observacionesLasCarganDuenoEncargadoYVeterinarioYTodosLasVen(Rol rol, int esperado) throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String tokenRol = tokenConRol(rol);

        api.post(tokenRol, eventos(), observacion(HOY, "Nota", List.of(vaca))).andExpect(status().is(esperado));
        api.get(tokenRol, eventos()).andExpect(status().isOk());
    }

    @Test
    void elVeterinarioNoPuedeRegistrarEventosDeManejo() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String tokenVet = tokenConRol(Rol.VETERINARIO);
        var movimiento = observacion(HOY, "X", List.of(vaca));
        movimiento.put("tipo", "MOVIMIENTO");

        api.post(tokenVet, eventos(), movimiento).andExpect(status().isForbidden());
    }

    @Test
    void otraOrganizacionNoPuedeVerNiRegistrarNiAnular() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String eventoId = idDelPrimerEvento(api.post(token, eventos(), observacion(HOY, "Nota", List.of(vaca))));
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, eventos()).andExpect(status().isNotFound());
        api.post(tokenB, eventos(), observacion(HOY, "Intruso", List.of())).andExpect(status().isNotFound());
        api.get(tokenB, eventos() + "/" + eventoId).andExpect(status().isNotFound());
        api.delete(tokenB, eventos() + "/" + eventoId).andExpect(status().isNotFound());

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(1));
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url("eventos");
    }

    private String url(String recurso) {
        return url(establecimientoId, recurso);
    }

    private static String url(String establecimiento, String recurso) {
        return "/api/establecimientos/" + establecimiento + "/" + recurso;
    }

    private static Map<String, Object> observacion(String fecha, String texto, List<String> animalIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "OBSERVACION");
        body.put("fecha", fecha);
        body.put("animalIds", animalIds);
        body.put("datos", Map.of("texto", texto));
        return body;
    }

    private static Map<String, Object> animal(String caravana, String sexo, String categoria) {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", sexo);
        body.put("categoria", categoria);
        return body;
    }

    private String crearAnimal(String caravana, String sexo, String categoria) throws Exception {
        return api.leer(api.post(token, url("animales"), animal(caravana, sexo, categoria))
            .andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String idDelPrimerEvento(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        String json = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("eventos").get(0).get("id").asText();
    }

    /** Usuario nuevo de la organización agregado al establecimiento con ese rol. */
    private String tokenConRol(Rol rol) throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, url("miembros"), Map.of("email", email, "rol", rol.name())).andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
