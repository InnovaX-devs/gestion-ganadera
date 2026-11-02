package com.innovax.ganadera.evento.tipos;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
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

/** Issue #19: eventos Servicio y Tacto, y el estado reproductivo en la ficha. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServicioTactoTest {

    private static final LocalDate HOY = LocalDate.now();

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
        establecimientoId = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Servicio ----------

    @Test
    void servicioNaturalGuardaLosTorosYLaVacaQuedaEnServicio() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String toro = crearAnimal("T-1", "MACHO", "TORO");

        api.post(token, eventos(), servicio(List.of(vaca), natural(null, toro)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.eventos[0].tipo").value("SERVICIO"))
            .andExpect(jsonPath("$.eventos[0].datos.tipo").value("NATURAL"))
            .andExpect(jsonPath("$.eventos[0].datos.toros[0].id").value(toro))
            .andExpect(jsonPath("$.eventos[0].datos.toros[0].caravana").value("T-1"));

        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("EN_SERVICIO"))
            .andExpect(jsonPath("$.estadoReproductivo.desde").value(HOY.toString()))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoServicio.tipo").value("NATURAL"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoServicio.toros[0]").value("T-1"));
    }

    @Test
    void servicioPorRodeoVaSoloALasVacasYVaquillonas() throws Exception {
        String rodeoId = api.leer(api.post(token, url("rodeos"), Map.of("nombre", "Cría")), "id");
        String v1 = crearAnimal("V-1", "HEMBRA", "VACA", rodeoId);
        String v2 = crearAnimal("V-2", "HEMBRA", "VACA", rodeoId);
        String vq = crearAnimal("VQ-1", "HEMBRA", "VAQUILLONA", rodeoId);
        String toro = crearAnimal("T-1", "MACHO", "TORO", rodeoId);
        String ternera = crearAnimal("TE-1", "HEMBRA", "TERNERA", rodeoId);

        var body = servicio(List.of(), natural(HOY.plusDays(90).toString(), toro));
        body.put("rodeoId", rodeoId);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(3))
            .andExpect(jsonPath("$.eventos[*].animal.nombre", containsInAnyOrder("V-1", "V-2", "VQ-1")));

        for (String id : List.of(v1, v2, vq)) {
            api.get(token, animal(id)).andExpect(jsonPath("$.estadoReproductivo.estado").value("EN_SERVICIO"));
        }
        api.get(token, animal(toro)).andExpect(jsonPath("$.estadoReproductivo").doesNotExist());
        api.get(token, animal(ternera)).andExpect(jsonPath("$.estadoReproductivo").doesNotExist());
        api.get(token, eventos() + "?animalId=" + ternera).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unRodeoSinHembrasDeCriaNoRecibeServicio() throws Exception {
        String rodeoId = api.leer(api.post(token, url("rodeos"), Map.of("nombre", "Toros")), "id");
        crearAnimal("T-1", "MACHO", "TORO", rodeoId);

        var body = servicio(List.of(), natural(null));
        body.put("rodeoId", rodeoId);
        api.post(token, eventos(), body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "El rodeo Toros no tiene animales a los que se pueda registrar un servicio"));
    }

    @Test
    void elegidosUnoPorUnoTienenQueSerVacasOVaquillonas() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String ternera = crearAnimal("TE-1", "HEMBRA", "TERNERA");

        api.post(token, eventos(), servicio(List.of(vaca, ternera), natural(null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "TE-1 no es vaca ni vaquillona: el servicio se registra solo en hembras de cría"));
        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void inseminacionConPajuelaOConToro() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String t1 = crearAnimal("T-1", "MACHO", "TORO");
        String t2 = crearAnimal("T-2", "MACHO", "TORITO");

        api.post(token, eventos(), servicio(List.of(vaca), inseminacion("  ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.pajuela"));

        var dosToros = inseminacion(null);
        dosToros.put("toroIds", List.of(t1, t2));
        api.post(token, eventos(), servicio(List.of(vaca), dosToros))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.toroIds"));

        api.post(token, eventos(), servicio(List.of(vaca), inseminacion(" Angus Rito 707 ")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.pajuela").value("Angus Rito 707"));

        // Una inseminación ya no está "en servicio": quedó servida hasta el tacto
        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("SERVIDA"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoServicio.pajuela").value("Angus Rito 707"));
    }

    @Test
    void losTorosTienenQueSerMachosEnterosDelEstablecimiento() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String novillo = crearAnimal("N-1", "MACHO", "NOVILLO");

        api.post(token, eventos(), servicio(List.of(vaca), natural(null, novillo)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("N-1 no es toro ni torito"));
        api.post(token, eventos(), servicio(List.of(vaca), natural(null, UUID.randomUUID().toString())))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void laFechaDeFinNoPuedeSerAnteriorAlInicio() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), servicio(List.of(vaca), natural(HOY.minusDays(1).toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.fechaFin"));

        api.post(token, eventos(), servicio(List.of(vaca), natural(HOY.plusDays(400).toString())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.fechaFin"));
    }

    @Test
    void unServicioNaturalTerminadoQuedaComoServida() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        var body = servicio(List.of(vaca), natural(HOY.minusDays(10).toString()));
        body.put("fecha", HOY.minusDays(100).toString());
        api.post(token, eventos(), body).andExpect(status().isCreated());

        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("SERVIDA"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoServicio.fechaFin").value(HOY.minusDays(10).toString()));
    }

    // ---------- Tacto ----------

    @Test
    void tactoPrenadaCalculaLaFechaEstimadaDeParto() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        // 3 meses al 1/3/2026: se preñó el 30/11/2025 y pare 283 días después
        api.post(token, eventos(), tacto(List.of(vaca), "2026-03-01", "PRENADA", 3, " Dra. Gómez "))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.resultado").value("PRENADA"))
            .andExpect(jsonPath("$.eventos[0].datos.mesesGestacion").value(3))
            .andExpect(jsonPath("$.eventos[0].datos.veterinario").value("Dra. Gómez"))
            .andExpect(jsonPath("$.eventos[0].datos.fechaEstimadaParto").value("2026-09-09"));

        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("PRENADA"))
            .andExpect(jsonPath("$.estadoReproductivo.fechaEstimadaParto").value("2026-09-09"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoTacto.mesesGestacion").value(3))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoTacto.veterinario").value("Dra. Gómez"));
    }

    @Test
    void laPrenadaNecesitaMesesYEnLaVaciaSeDescartan() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), tacto(List.of(vaca), HOY.toString(), "PRENADA", null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.mesesGestacion"));
        api.post(token, eventos(), tacto(List.of(vaca), HOY.toString(), "PRENADA", 10, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.mesesGestacion"));
        api.post(token, eventos(), tacto(List.of(vaca), HOY.toString(), null, null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.resultado"));

        api.post(token, eventos(), tacto(List.of(vaca), HOY.toString(), "VACIA", 4, null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.mesesGestacion").doesNotExist())
            .andExpect(jsonPath("$.eventos[0].datos.fechaEstimadaParto").doesNotExist());
        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("VACIA"))
            .andExpect(jsonPath("$.estadoReproductivo.fechaEstimadaParto").doesNotExist());
    }

    @Test
    void unaPrenezIncompatibleConUnPartoPosteriorSeRechaza() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        api.post(token, eventos(), nacimiento(vaca, "2026-01-10")).andExpect(status().isCreated());

        // 3 meses al 1/3 = preñada desde fines de noviembre, pero parió el 10/1
        api.post(token, eventos(), tacto(List.of(vaca), "2026-03-01", "PRENADA", 3, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(containsString("V-1 parió el 10/01/2026")));

        // 1 mes sí: se preñó después del parto
        api.post(token, eventos(), tacto(List.of(vaca), "2026-03-01", "PRENADA", 1, null))
            .andExpect(status().isCreated());
    }

    // ---------- Estado reproductivo ----------

    @Test
    void elEstadoSigueAlUltimoEventoYSeCorrigeAlAnular() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        api.get(token, animal(vaca)).andExpect(jsonPath("$.estadoReproductivo.estado").value("SIN_DATOS"));

        var primerServicio = servicio(List.of(vaca), natural(HOY.minusDays(60).toString()));
        primerServicio.put("fecha", HOY.minusDays(150).toString());
        api.post(token, eventos(), primerServicio).andExpect(status().isCreated());
        String tactoId = idDelEvento(api.post(token, eventos(),
            tacto(List.of(vaca), HOY.minusDays(30).toString(), "VACIA", null, "Dr. Pérez")));
        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("VACIA"))
            .andExpect(jsonPath("$.estadoReproductivo.desde").value(HOY.minusDays(30).toString()));

        // Se la vuelve a servir: manda el servicio, que es más nuevo que el tacto
        api.post(token, eventos(), servicio(List.of(vaca), inseminacion("Pajuela X")))
            .andExpect(status().isCreated());
        api.get(token, animal(vaca)).andExpect(jsonPath("$.estadoReproductivo.estado").value("SERVIDA"));

        // Anular el tacto no cambia lo que manda (el servicio más nuevo), pero lo saca del resumen
        api.delete(token, eventos() + "/" + tactoId).andExpect(status().isNoContent());
        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("SERVIDA"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoTacto").doesNotExist());
    }

    @Test
    void despuesDelPartoQuedaParida() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        api.post(token, eventos(), tacto(List.of(vaca), HOY.minusDays(120).toString(), "PRENADA", 5, null))
            .andExpect(status().isCreated());
        api.post(token, eventos(), nacimiento(vaca, HOY.toString())).andExpect(status().isCreated());

        api.get(token, animal(vaca))
            .andExpect(jsonPath("$.estadoReproductivo.estado").value("PARIDA"))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoParto").value(HOY.toString()))
            .andExpect(jsonPath("$.estadoReproductivo.ultimoTacto.resultado").value("PRENADA"));
    }

    @Test
    void elListadoTambienTraeElEstado() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        crearAnimal("T-1", "MACHO", "TORO");
        api.post(token, eventos(), tacto(List.of(vaca), HOY.toString(), "PRENADA", 2, null))
            .andExpect(status().isCreated());

        api.get(token, url("animales"))
            .andExpect(jsonPath("$.contenido[0].caravana").value("T-1"))
            .andExpect(jsonPath("$.contenido[0].estadoReproductivo").doesNotExist())
            .andExpect(jsonPath("$.contenido[1].estadoReproductivo.estado").value("PRENADA"));
    }

    // ---------- Permisos ----------

    @Test
    void elVeterinarioTactaPeroNoRegistraServicios() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        String veterinario = tokenConRol(Rol.VETERINARIO);

        api.post(veterinario, eventos(), tacto(List.of(vaca), HOY.toString(), "VACIA", null, null))
            .andExpect(status().isCreated());
        api.post(veterinario, eventos(), servicio(List.of(vaca), natural(null)))
            .andExpect(status().isForbidden());
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url("eventos");
    }

    private String animal(String id) {
        return url("animales") + "/" + id;
    }

    private String url(String recurso) {
        return "/api/establecimientos/" + establecimientoId + "/" + recurso;
    }

    private static Map<String, Object> servicio(List<String> animalIds, Map<String, Object> datos) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "SERVICIO");
        body.put("fecha", HOY.toString());
        body.put("animalIds", animalIds);
        body.put("datos", datos);
        return body;
    }

    private static Map<String, Object> natural(String fechaFin, String... toroIds) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipo", "NATURAL");
        datos.put("fechaFin", fechaFin);
        datos.put("toroIds", List.of(toroIds));
        return datos;
    }

    private static Map<String, Object> inseminacion(String pajuela) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipo", "INSEMINACION");
        datos.put("pajuela", pajuela);
        return datos;
    }

    private static Map<String, Object> tacto(List<String> animalIds, String fecha, String resultado,
                                             Integer meses, String veterinario) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("resultado", resultado);
        datos.put("mesesGestacion", meses);
        datos.put("veterinario", veterinario);
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "TACTO");
        body.put("fecha", fecha);
        body.put("animalIds", animalIds);
        body.put("datos", datos);
        return body;
    }

    private static Map<String, Object> nacimiento(String madreId, String fecha) {
        return Map.of("tipo", "NACIMIENTO", "fecha", fecha, "animalIds", List.of(madreId),
            "datos", Map.of("tipoParto", "NORMAL",
                "crias", List.of(Map.of("sexo", "MACHO", "caravana", "C-" + UUID.randomUUID().toString().substring(0, 8)))));
    }

    private String crearAnimal(String caravana, String sexo, String categoria) throws Exception {
        return crearAnimal(caravana, sexo, categoria, null);
    }

    private String crearAnimal(String caravana, String sexo, String categoria, String rodeoId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", sexo);
        body.put("categoria", categoria);
        body.put("rodeoId", rodeoId);
        return api.leer(api.post(token, url("animales"), body).andExpect(status().isCreated()), "id");
    }

    private String idDelEvento(ResultActions resultado) throws Exception {
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
