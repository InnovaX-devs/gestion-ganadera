package com.innovax.ganadera.evento.tipos;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
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

/** Issue #18: evento Nacimiento. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NacimientoTest {

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
        establecimientoId = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Registro ----------

    @Test
    void creaLaCriaVinculadaALaMadreEnSuLoteYRodeo() throws Exception {
        String loteId = api.leer(api.post(token, url("lotes"), Map.of("nombre", "Parición")), "id");
        String rodeoId = api.leer(api.post(token, url("rodeos"), Map.of("nombre", "Cría")), "id");
        var vaca = animal("V-1", "HEMBRA", "VACA");
        vaca.put("loteId", loteId);
        vaca.put("rodeoId", rodeoId);
        String madre = api.leer(api.post(token, url("animales"), vaca).andExpect(status().isCreated()), "id");

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", " c-101 ", 34.5)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.eventos[0].tipo").value("NACIMIENTO"))
            .andExpect(jsonPath("$.eventos[0].animal.nombre").value("V-1"))
            .andExpect(jsonPath("$.eventos[0].datos.tipoParto").value("NORMAL"))
            .andExpect(jsonPath("$.eventos[0].datos.crias[0].caravana").value("C-101"))
            .andExpect(jsonPath("$.eventos[0].datos.crias[0].pesoKg").value(34.5));

        api.get(token, url("animales") + "/" + madre + "/crias")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].caravana").value("C-101"))
            .andExpect(jsonPath("$[0].sexo").value("MACHO"))
            .andExpect(jsonPath("$[0].categoria").value("TERNERO"))
            .andExpect(jsonPath("$[0].castrado").value(false))
            .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
            .andExpect(jsonPath("$[0].fechaNacimiento").value(HOY))
            .andExpect(jsonPath("$[0].madre.id").value(madre))
            .andExpect(jsonPath("$[0].lote.id").value(loteId))
            .andExpect(jsonPath("$[0].rodeo.id").value(rodeoId));
    }

    @Test
    void laVaquillonaPasaAVacaYQuedaEnSuHistorialDeCategorias() throws Exception {
        String madre = crearAnimal("VQ-1", "HEMBRA", "VAQUILLONA");

        api.post(token, eventos(), nacimiento(madre, HOY, "ASISTIDO", cria("HEMBRA", "C-1", null)))
            .andExpect(status().isCreated());

        api.get(token, url("animales") + "/" + madre).andExpect(jsonPath("$.categoria").value("VACA"));
        api.get(token, url("animales") + "/" + madre + "/cambios-categoria")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].categoriaAnterior").value("VAQUILLONA"))
            .andExpect(jsonPath("$[0].categoriaNueva").value("VACA"))
            .andExpect(jsonPath("$[0].motivo").value("PARTO"))
            .andExpect(jsonPath("$[0].fecha").value(HOY));
        api.get(token, url("animales") + "/" + madre + "/crias")
            .andExpect(jsonPath("$[0].categoria").value("TERNERA"));
    }

    @Test
    void unaVacaSigueSiendoVacaSinCambiosDeCategoria() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), nacimiento(madre, HOY, "CESAREA", cria("HEMBRA", "C-1", null)))
            .andExpect(status().isCreated());

        api.get(token, url("animales") + "/" + madre).andExpect(jsonPath("$.categoria").value("VACA"));
        api.get(token, url("animales") + "/" + madre + "/cambios-categoria").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void mellizosCreanDosCriasEnUnSoloEvento() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL",
                cria("MACHO", "C-1", 30.0), cria("HEMBRA", "C-2", 28.0)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.eventos[0].datos.crias", hasSize(2)));

        api.get(token, url("animales") + "/" + madre + "/crias")
            .andExpect(jsonPath("$[*].caravana", containsInAnyOrder("C-1", "C-2")))
            .andExpect(jsonPath("$[*].categoria", containsInAnyOrder("TERNERO", "TERNERA")));
    }

    @Test
    void elNacidoMuertoQuedaEnElEventoPeroNoSeCreaComoAnimal() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VAQUILLONA");

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL",
                cria("MACHO", "C-1", null), muerta("HEMBRA", "LA-CARAVANA-SE-IGNORA")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.crias[1].nacidoMuerto").value(true))
            .andExpect(jsonPath("$.eventos[0].datos.crias[1].caravana").doesNotExist());

        api.get(token, url("animales") + "/" + madre + "/crias")
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].caravana").value("C-1"));
    }

    @Test
    void siTodasNacieronMuertasCuentaIgualComoParto() throws Exception {
        String madre = crearAnimal("VQ-1", "HEMBRA", "VAQUILLONA");

        api.post(token, eventos(), nacimiento(madre, HOY, "ASISTIDO", muerta("MACHO", null)))
            .andExpect(status().isCreated());

        api.get(token, url("animales") + "/" + madre).andExpect(jsonPath("$.categoria").value("VACA"));
        api.get(token, url("animales") + "/" + madre + "/crias").andExpect(jsonPath("$", hasSize(0)));
        api.get(token, url("animales/resumen")).andExpect(jsonPath("$.total").value(1));
    }

    // ---------- Validaciones ----------

    @Test
    void lasCriasVivasNecesitanCaravanaSinRepetir() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "  ", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.crias[0].caravana"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Ingresá la caravana de la cría"));

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "c-1", null), cria("HEMBRA", "C-1", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.crias[1].caravana"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Está repetida en otra cría"));

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "v-1", null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya hay un animal con la caravana V-1 en este establecimiento"));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
        api.get(token, url("animales/resumen")).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void datosDelPartoSeValidan() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");

        var sinCrias = nacimiento(madre, HOY, "NORMAL");
        api.post(token, eventos(), sinCrias)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.crias"));

        api.post(token, eventos(), nacimiento(madre, HOY, null, cria("MACHO", "C-1", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.tipoParto"));

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL",
                cria("MACHO", "C-1", null), cria("MACHO", "C-2", null), cria("MACHO", "C-3", null), cria("MACHO", "C-4", null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.crias"));

        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria(null, "C-1", 250.0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field",
                containsInAnyOrder("datos.crias[0].sexo", "datos.crias[0].pesoKg")));
    }

    @Test
    void laMadreTieneQueSerUnaSolaHembra() throws Exception {
        String toro = crearAnimal("T-1", "MACHO", "TORO");
        String v1 = crearAnimal("V-1", "HEMBRA", "VACA");
        String v2 = crearAnimal("V-2", "HEMBRA", "VACA");

        api.post(token, eventos(), nacimiento(toro, HOY, "NORMAL", cria("MACHO", "C-1", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La madre tiene que ser hembra"));

        var dosMadres = nacimiento(v1, HOY, "NORMAL", cria("MACHO", "C-1", null));
        dosMadres.put("animalIds", List.of(v1, v2));
        api.post(token, eventos(), dosMadres).andExpect(status().isUnprocessableEntity());

        var sinMadre = nacimiento(v1, HOY, "NORMAL", cria("MACHO", "C-1", null));
        sinMadre.put("animalIds", List.of());
        api.post(token, eventos(), sinMadre).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noSeCarganDosPartosDeLaMismaMadreConMenosDeUnaGestacionEntreMedio() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");
        LocalDate hoy = LocalDate.now();

        api.post(token, eventos(), nacimiento(madre, hoy.minusDays(10).toString(), "NORMAL", cria("MACHO", "C-1", null)))
            .andExpect(status().isCreated());

        // Mellizo cargado aparte: se rechaza y se explica cómo cargarlo
        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("HEMBRA", "C-2", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Si fueron mellizos")));

        // Un parto del año anterior sí se puede cargar
        api.post(token, eventos(), nacimiento(madre, hoy.minusDays(400).toString(), "NORMAL", cria("HEMBRA", "C-3", null)))
            .andExpect(status().isCreated());
    }

    // ---------- Anulación ----------

    @Test
    void anularBorraLasCriasYLaMadreVuelveAVaquillona() throws Exception {
        String madre = crearAnimal("VQ-1", "HEMBRA", "VAQUILLONA");
        String eventoId = idDelEvento(api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL",
            cria("MACHO", "C-1", null), cria("HEMBRA", "C-2", null))));

        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());

        api.get(token, url("animales") + "/" + madre).andExpect(jsonPath("$.categoria").value("VAQUILLONA"));
        api.get(token, url("animales") + "/" + madre + "/cambios-categoria").andExpect(jsonPath("$", hasSize(0)));
        api.get(token, url("animales") + "/" + madre + "/crias").andExpect(jsonPath("$", hasSize(0)));
        api.get(token, url("animales/resumen")).andExpect(jsonPath("$.total").value(1));

        // La caravana queda libre para cargarlo bien
        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "C-1", null)))
            .andExpect(status().isCreated());
    }

    @Test
    void siLaMadreTieneOtroPartoSigueSiendoVaca() throws Exception {
        String madre = crearAnimal("VQ-1", "HEMBRA", "VAQUILLONA");
        LocalDate hoy = LocalDate.now();
        // El primero es el que la pasó a vaca
        String primero = idDelEvento(api.post(token, eventos(),
            nacimiento(madre, hoy.minusDays(400).toString(), "NORMAL", cria("MACHO", "C-1", null))));
        api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "C-2", null)))
            .andExpect(status().isCreated());

        api.delete(token, eventos() + "/" + primero).andExpect(status().isNoContent());

        api.get(token, url("animales") + "/" + madre).andExpect(jsonPath("$.categoria").value("VACA"));
    }

    @Test
    void noSeAnulaSiUnaCriaYaTieneEventos() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");
        String eventoId = idDelEvento(api.post(token, eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "C-1", null))));
        String criaId = objectMapper.readTree(api.get(token, url("animales") + "/" + madre + "/crias")
            .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
        api.post(token, eventos(), Map.of("tipo", "OBSERVACION", "fecha", HOY, "animalIds", List.of(criaId),
            "datos", Map.of("texto", "Mamando bien"))).andExpect(status().isCreated());

        api.delete(token, eventos() + "/" + eventoId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "No se puede anular el nacimiento: la cría C-1 tiene eventos registrados. Anulalos primero."));

        api.get(token, url("animales") + "/" + madre + "/crias").andExpect(jsonPath("$", hasSize(1)));
        api.get(token, eventos() + "?tipo=NACIMIENTO").andExpect(jsonPath("$.total").value(1));
    }

    // ---------- Permisos ----------

    @Test
    void elVeterinarioNoRegistraNacimientos() throws Exception {
        String madre = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(tokenConRol(Rol.VETERINARIO), eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "C-1", null)))
            .andExpect(status().isForbidden());
        api.post(tokenConRol(Rol.ENCARGADO), eventos(), nacimiento(madre, HOY, "NORMAL", cria("MACHO", "C-1", null)))
            .andExpect(status().isCreated());
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url("eventos");
    }

    private String url(String recurso) {
        return "/api/establecimientos/" + establecimientoId + "/" + recurso;
    }

    @SafeVarargs
    private static Map<String, Object> nacimiento(String madreId, String fecha, String tipoParto,
                                                  Map<String, Object>... crias) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoParto", tipoParto);
        datos.put("crias", new ArrayList<>(List.of(crias)));
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "NACIMIENTO");
        body.put("fecha", fecha);
        body.put("animalIds", List.of(madreId));
        body.put("datos", datos);
        return body;
    }

    private static Map<String, Object> cria(String sexo, String caravana, Double pesoKg) {
        Map<String, Object> cria = new HashMap<>();
        cria.put("sexo", sexo);
        cria.put("caravana", caravana);
        cria.put("pesoKg", pesoKg);
        return cria;
    }

    private static Map<String, Object> muerta(String sexo, String caravana) {
        Map<String, Object> cria = cria(sexo, caravana, null);
        cria.put("nacidoMuerto", true);
        return cria;
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
