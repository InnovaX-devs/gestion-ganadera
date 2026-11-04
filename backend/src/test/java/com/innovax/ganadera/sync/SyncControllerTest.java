package com.innovax.ganadera.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #30: POST /api/sync, push y pull. Ver docs/sync.md. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncControllerTest {

    /** Ayer en cualquier zona horaria: "hoy" puede ser mañana para el servidor si el test corre de noche en UTC. */
    private static final String AYER = LocalDate.now().minusDays(1).toString();

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
    private String est;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        est = crearEstablecimiento("La Esperanza");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Pull ----------

    @Test
    void laPrimeraSincronizacionTraeTodoYLaSiguienteNada() throws Exception {
        String lote = crear("lotes", Map.of("nombre", "Recría"));
        String rodeo = crear("rodeos", Map.of("nombre", "Cría"));
        String producto = crear("productos-sanitarios", Map.of("nombre", "Ivomec", "tipo", "ANTIPARASITARIO",
            "diasCarencia", 28));
        String animal = crearAnimal("V-1", lote);
        String operacion = observacion(animal, "Rengueando");

        JsonNode r = sync(Map.of("establecimientoId", est));

        assertThat(r.get("hayMas").asBoolean()).isFalse();
        assertThat(r.at("/establecimiento/nombre").asText()).isEqualTo("La Esperanza");
        assertThat(r.at("/establecimiento/rol").asText()).isEqualTo("DUENO");
        assertThat(ids(r, "lotes")).containsExactly(lote);
        assertThat(ids(r, "rodeos")).containsExactly(rodeo);
        assertThat(ids(r, "productos")).containsExactly(producto);
        assertThat(ids(r, "animales")).containsExactly(animal);
        assertThat(r.at("/cambios/animales/0/caravana").asText()).isEqualTo("V-1");
        assertThat(r.at("/cambios/animales/0/loteId").asText()).isEqualTo(lote);
        assertThat(r.at("/cambios/animales/0/estado").asText()).isEqualTo("ACTIVO");
        assertThat(r.at("/cambios/eventos/0/operacionId").asText()).isEqualTo(operacion);
        assertThat(r.at("/cambios/eventos/0/animalId").asText()).isEqualTo(animal);
        assertThat(r.at("/cambios/eventos/0/datos/texto").asText()).isEqualTo("Rengueando");
        assertThat(r.at("/cambios/eventos/0/anulado").asBoolean()).isFalse();
        assertThat(r.get("bajas")).isEmpty();

        JsonNode siguiente = sync(Map.of("establecimientoId", est, "cursor", r.get("cursor").asText()));

        assertThat(totalDeCambios(siguiente)).isZero();
        assertThat(siguiente.get("hayMas").asBoolean()).isFalse();
    }

    @Test
    void conCursorTraeSoloLoQueCambio() throws Exception {
        String a1 = crearAnimal("V-1", null);
        crearAnimal("V-2", null);
        String cursor = sync(Map.of("establecimientoId", est)).get("cursor").asText();

        editarEnLaWeb(a1, "V-1", "Hereford", null);
        String lote = crear("lotes", Map.of("nombre", "Nuevo"));

        JsonNode r = sync(Map.of("establecimientoId", est, "cursor", cursor));
        assertThat(ids(r, "animales")).containsExactly(a1);
        assertThat(r.at("/cambios/animales/0/raza").asText()).isEqualTo("Hereford");
        assertThat(ids(r, "lotes")).containsExactly(lote);
    }

    @Test
    void paginaHastaQueNoQuedaNadaSinRepetirNiSaltearFilas() throws Exception {
        Set<String> esperados = new HashSet<>();
        esperados.add(crear("lotes", Map.of("nombre", "L1")));
        esperados.add(crear("rodeos", Map.of("nombre", "R1")));
        for (int i = 1; i <= 5; i++) esperados.add(crearAnimal("A-" + i, null));
        // Varios animales con el mismo updated_at: el id desempata
        jdbc.update("UPDATE animal SET updated_at = TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00'");

        List<String> recibidos = new ArrayList<>();
        String cursor = null;
        int vueltas = 0;
        boolean hayMas = true;
        while (hayMas) {
            Map<String, Object> body = new HashMap<>(Map.of("establecimientoId", est, "limite", 2));
            if (cursor != null) body.put("cursor", cursor);
            JsonNode r = sync(body);
            for (String tabla : List.of("lotes", "rodeos", "productos", "animales", "eventos")) {
                recibidos.addAll(ids(r, tabla));
            }
            assertThat(totalDeCambios(r)).isLessThanOrEqualTo(2);
            cursor = r.get("cursor").asText();
            hayMas = r.get("hayMas").asBoolean();
            assertThat(++vueltas).isLessThan(10);
        }

        assertThat(recibidos).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(esperados);
    }

    @Test
    void lasBajasLleganCuandoUnAnimalSeBorraOSeTraslada() throws Exception {
        String borrado = crearAnimal("B-1", null);
        String trasladado = crearAnimal("T-1", null);
        String otro = crearEstablecimiento("El Ombú");
        String cursor = sync(Map.of("establecimientoId", est)).get("cursor").asText();
        String cursorOtro = sync(Map.of("establecimientoId", otro)).get("cursor").asText();

        api.delete(token, "/api/establecimientos/" + est + "/animales/" + borrado).andExpect(status().isNoContent());
        String movimiento = api.leer(api.post(token, eventos(est), Map.of(
                "tipo", "MOVIMIENTO", "fecha", AYER, "animalIds", List.of(trasladado),
                "datos", Map.of("establecimientoDestinoId", otro)))
            .andExpect(status().isCreated()), "operacionId");

        JsonNode origen = sync(Map.of("establecimientoId", est, "cursor", cursor));
        assertThat(bajas(origen)).containsExactlyInAnyOrder(borrado, trasladado);
        assertThat(ids(origen, "animales")).doesNotContain(trasladado);
        JsonNode destino = sync(Map.of("establecimientoId", otro, "cursor", cursorOtro));
        assertThat(ids(destino, "animales")).containsExactly(trasladado);
        assertThat(bajas(destino)).isEmpty();

        // Se anula el traslado: el animal vuelve al origen y ahí no se manda su baja
        String eventoId = jdbc.queryForObject("SELECT id FROM evento WHERE operacion_id = ?", String.class,
            UUID.fromString(movimiento));
        api.delete(token, eventos(est) + "/" + eventoId).andExpect(status().isNoContent());

        JsonNode deNuevo = sync(Map.of("establecimientoId", est));
        assertThat(ids(deNuevo, "animales")).contains(trasladado);
        assertThat(bajas(deNuevo)).containsExactly(borrado);
        assertThat(bajas(sync(Map.of("establecimientoId", otro, "cursor", destino.get("cursor").asText()))))
            .containsExactly(trasladado);
    }

    // ---------- Push ----------

    @Test
    void registraUnEventoYReenviarloNoLoDuplica() throws Exception {
        String animal = crearAnimal("V-1", null);
        String id = UUID.randomUUID().toString();
        Map<String, Object> op = registrarObservacion(id, animal, "Sin conexión");

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(op)));
        JsonNode reenvio = sync(Map.of("establecimientoId", est, "operaciones", List.of(op)));

        assertThat(r.at("/resultados/0/id").asText()).isEqualTo(id);
        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(r.at("/resultados/0/operacionId").asText()).isEqualTo(id);
        // El pull de la misma llamada ya trae lo que se acaba de registrar
        assertThat(r.at("/cambios/eventos/0/operacionId").asText()).isEqualTo(id);
        assertThat(reenvio.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento", Integer.class)).isEqualTo(1);
    }

    @Test
    void unaOperacionRechazadaNoFrenaLasDemas() throws Exception {
        String vendido = crearAnimal("V-1", null);
        String activo = crearAnimal("V-2", null);
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendido));

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(
            registrarObservacion(UUID.randomUUID().toString(), vendido, "Llegó tarde"),
            Map.of("id", UUID.randomUUID().toString(), "tipo", "REGISTRAR_EVENTO",
                "evento", Map.of("tipo", "OBSERVACION", "datos", Map.of("texto", "Sin fecha"))),
            registrarObservacion(UUID.randomUUID().toString(), activo, "Bien"))));

        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("RECHAZADO");
        assertThat(r.at("/resultados/0/mensaje").asText()).contains("no está en este establecimiento");
        assertThat(r.at("/resultados/1/estado").asText()).isEqualTo("RECHAZADO");
        assertThat(r.at("/resultados/1/errores/0/field").asText()).isEqualTo("evento.fecha");
        assertThat(r.at("/resultados/2/estado").asText()).isEqualTo("APLICADO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento", Integer.class)).isEqualTo(1);
    }

    @Test
    void registraPorAnimalComoLaGrillaDePesajes() throws Exception {
        String a1 = crearAnimal("N-1", null);
        String a2 = crearAnimal("N-2", null);

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(Map.of(
            "id", UUID.randomUUID().toString(), "tipo", "REGISTRAR_EVENTOS_POR_ANIMAL", "eventoTipo", "PESAJE",
            "fecha", AYER,
            "items", List.of(Map.of("animalId", a1, "datos", Map.of("kg", 310)),
                Map.of("animalId", a2, "datos", Map.of("kg", 295.5)))))));

        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(r.at("/cambios/eventos").size()).isEqualTo(2);
    }

    @Test
    void anularDosVecesEsLoMismoQueUna() throws Exception {
        String animal = crearAnimal("V-1", null);
        observacion(animal, "Para anular");
        String eventoId = jdbc.queryForObject("SELECT id FROM evento", String.class);
        Map<String, Object> anular = Map.of("id", UUID.randomUUID().toString(), "tipo", "ANULAR_EVENTO",
            "eventoId", eventoId);

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(anular)));
        JsonNode otraVez = sync(Map.of("establecimientoId", est, "operaciones", List.of(anular)));

        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(otraVez.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(r.at("/cambios/eventos/0/anulado").asBoolean()).isTrue();
    }

    // ---------- Conflictos: last-write-wins por campo ----------

    @Test
    void cadaCampoGanaElCambioMasNuevo() throws Exception {
        String animal = crearAnimal("V-1", null);
        Thread.sleep(20);
        // Juan edita sin conexión...
        Instant enElCelular = Instant.now();
        Thread.sleep(20);
        // ...y después Ana cambia la raza desde la web
        editarEnLaWeb(animal, "V-1", "Hereford", null);

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(
            actualizarAnimal(animal, enElCelular, Map.of("raza", "Angus", "observaciones", "Mansa")))));

        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("APLICADO");
        assertThat(textos(r.at("/resultados/0/aplicados"))).containsExactly("observaciones");
        assertThat(textos(r.at("/resultados/0/descartados"))).containsExactly("raza");
        JsonNode ficha = ficha(animal);
        assertThat(ficha.get("raza").asText()).isEqualTo("Hereford");
        assertThat(ficha.get("observaciones").asText()).isEqualTo("Mansa");
    }

    @Test
    void unaEdicionPosteriorALaWebGanaYReenviarlaNoCambiaNada() throws Exception {
        String animal = crearAnimal("V-1", null);
        editarEnLaWeb(animal, "V-1", "Hereford", null);
        Thread.sleep(20);
        Map<String, Object> op = actualizarAnimal(animal, Instant.now(), Map.of("raza", "Angus", "caravana", "v-100"));

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(op)));
        JsonNode reenvio = sync(Map.of("establecimientoId", est, "operaciones", List.of(op)));

        assertThat(textos(r.at("/resultados/0/aplicados"))).containsExactlyInAnyOrder("raza", "caravana");
        assertThat(textos(reenvio.at("/resultados/0/descartados"))).containsExactlyInAnyOrder("raza", "caravana");
        assertThat(ficha(animal).get("raza").asText()).isEqualTo("Angus");
        assertThat(ficha(animal).get("caravana").asText()).isEqualTo("V-100");
    }

    @Test
    void unRelojAdelantadoNoGanaParaSiempre() throws Exception {
        String animal = crearAnimal("V-1", null);
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        sync(Map.of("establecimientoId", est, "operaciones", List.of(
            actualizarAnimal(animal, manana, Map.of("raza", "Angus")))));
        Thread.sleep(20);

        // Si se hubiera guardado "mañana", esta edición de ahora perdería
        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(
            actualizarAnimal(animal, Instant.now(), Map.of("raza", "Brangus")))));

        assertThat(textos(r.at("/resultados/0/aplicados"))).containsExactly("raza");
        assertThat(ficha(animal).get("raza").asText()).isEqualTo("Brangus");
    }

    @Test
    void unValorInvalidoRechazaLaEdicionEntera() throws Exception {
        crearAnimal("V-1", null);
        String animal = crearAnimal("V-2", null);
        Thread.sleep(20);

        JsonNode r = sync(Map.of("establecimientoId", est, "operaciones", List.of(
            actualizarAnimal(animal, Instant.now(), Map.of("caravana", "V-1", "raza", "Angus")),
            actualizarAnimal(animal, Instant.now(), Map.of("categoria", "TORO")))));

        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("RECHAZADO");
        assertThat(r.at("/resultados/0/mensaje").asText()).contains("caravana V-1");
        assertThat(r.at("/resultados/1/estado").asText()).isEqualTo("RECHAZADO");
        assertThat(r.at("/resultados/1/mensaje").asText()).contains("no se puede editar sin conexión");
        assertThat(ficha(animal).has("raza")).isFalse();
    }

    // ---------- Permisos y errores ----------

    @Test
    void unSocioSincronizaPeroNoPuedeEditar() throws Exception {
        String animal = crearAnimal("V-1", null);
        String socio = tokenConRol(Rol.SOCIO);

        JsonNode r = json(api.post(socio, "/api/sync", Map.of("establecimientoId", est, "operaciones", List.of(
                actualizarAnimal(animal, Instant.now(), Map.of("raza", "Angus")))))
            .andExpect(status().isOk()));

        assertThat(r.at("/establecimiento/rol").asText()).isEqualTo("SOCIO");
        assertThat(ids(r, "animales")).containsExactly(animal);
        assertThat(r.at("/resultados/0/estado").asText()).isEqualTo("RECHAZADO");
        assertThat(r.at("/resultados/0/mensaje").asText()).contains("permiso");
    }

    @Test
    void erroresDelPedido() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        api.post(tokenB, "/api/sync", Map.of("establecimientoId", est)).andExpect(status().isNotFound());
        api.post(null, "/api/sync", Map.of("establecimientoId", est)).andExpect(status().isUnauthorized());
        api.post(token, "/api/sync", Map.of()).andExpect(status().isBadRequest());
        api.post(token, "/api/sync", Map.of("establecimientoId", est, "limite", 5000))
            .andExpect(status().isBadRequest());

        api.post(token, "/api/sync", Map.of("establecimientoId", est, "cursor", "basura"))
            .andExpect(status().isUnprocessableEntity());
        String otro = crearEstablecimiento("El Ombú");
        String cursorDeOtro = sync(Map.of("establecimientoId", otro)).get("cursor").asText();
        api.post(token, "/api/sync", Map.of("establecimientoId", est, "cursor", cursorDeOtro))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(Cursor.INVALIDO));
    }

    // ---------- Helpers ----------

    private JsonNode sync(Map<String, Object> body) throws Exception {
        return json(api.post(token, "/api/sync", body).andExpect(status().isOk()));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode respuesta, String tabla) {
        List<String> ids = new ArrayList<>();
        respuesta.path("cambios").path(tabla).forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private static List<String> bajas(JsonNode respuesta) {
        List<String> ids = new ArrayList<>();
        respuesta.path("bajas").forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private static List<String> textos(JsonNode lista) {
        List<String> textos = new ArrayList<>();
        lista.forEach(n -> textos.add(n.asText()));
        return textos;
    }

    private static int totalDeCambios(JsonNode respuesta) {
        int total = 0;
        for (JsonNode tabla : respuesta.get("cambios")) total += tabla.size();
        return total + respuesta.get("bajas").size();
    }

    private static Map<String, Object> registrarObservacion(String id, String animal, String texto) {
        return Map.of("id", id, "tipo", "REGISTRAR_EVENTO", "evento", Map.of(
            "tipo", "OBSERVACION", "fecha", AYER, "animalIds", List.of(animal),
            "datos", Map.of("texto", texto)));
    }

    private static Map<String, Object> actualizarAnimal(String animal, Instant cuando, Map<String, Object> cambios) {
        return Map.of("id", UUID.randomUUID().toString(), "tipo", "ACTUALIZAR_ANIMAL", "animalId", animal,
            "modificadoEn", cuando.toString(), "cambios", cambios);
    }

    private String observacion(String animal, String texto) throws Exception {
        return api.leer(api.post(token, eventos(est), Map.of("tipo", "OBSERVACION",
                "fecha", AYER, "animalIds", List.of(animal), "datos", Map.of("texto", texto)))
            .andExpect(status().isCreated()), "operacionId");
    }

    private void editarEnLaWeb(String animal, String caravana, String raza, String observaciones) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("caravana", caravana, "sexo", "HEMBRA", "categoria", "VACA"));
        body.put("raza", raza);
        body.put("observaciones", observaciones);
        api.put(token, "/api/establecimientos/" + est + "/animales/" + animal, body).andExpect(status().isOk());
    }

    private JsonNode ficha(String animal) throws Exception {
        return json(api.get(token, "/api/establecimientos/" + est + "/animales/" + animal).andExpect(status().isOk()));
    }

    private String crearAnimal(String caravana, String lote) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("caravana", caravana, "sexo", "HEMBRA", "categoria", "VACA"));
        if (lote != null) body.put("loteId", lote);
        return crear("animales", body);
    }

    private String crear(String recurso, Map<String, Object> body) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos/" + est + "/" + recurso, body)
            .andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String nombre) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private static String eventos(String establecimiento) {
        return "/api/establecimientos/" + establecimiento + "/eventos";
    }

    /** Usuario nuevo de la misma organización con ese rol en el establecimiento. */
    private String tokenConRol(Rol rol) throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, "/api/establecimientos/" + est + "/miembros", Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
