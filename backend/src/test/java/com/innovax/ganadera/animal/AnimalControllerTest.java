package com.innovax.ganadera.animal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
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

/** Issue #13. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnimalControllerTest {

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

    // ---------- Alta y consulta ----------

    @Test
    void creaUnAnimalConTodosSusDatos() throws Exception {
        String loteId = crearAgrupacion("lotes", "Recría");
        String rodeoId = crearAgrupacion("rodeos", "Cría");
        String madreId = crear(animal(" a-100 ", "HEMBRA", "VACA"));
        String padreId = crear(animal("T-1", "MACHO", "TORO"));

        var body = animal("  b-200 ", "HEMBRA", "TERNERA");
        body.put("caravanaElectronica", " 032000123456789 ");
        body.put("raza", " Aberdeen Angus ");
        body.put("fechaNacimiento", "2026-03-15");
        body.put("fechaNacimientoEstimada", true);
        body.put("loteId", loteId);
        body.put("rodeoId", rodeoId);
        body.put("madreId", madreId);
        body.put("padreId", padreId);
        body.put("observaciones", "Nació en el potrero 4");

        String id = api.leer(api.post(token, url(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.caravana").value("B-200"))
            .andExpect(jsonPath("$.caravanaElectronica").value("032000123456789"))
            .andExpect(jsonPath("$.raza").value("Aberdeen Angus"))
            .andExpect(jsonPath("$.estado").value("ACTIVO"))
            .andExpect(jsonPath("$.lote.nombre").value("Recría"))
            .andExpect(jsonPath("$.rodeo.nombre").value("Cría"))
            .andExpect(jsonPath("$.madre.nombre").value("A-100"))
            .andExpect(jsonPath("$.padre.nombre").value("T-1")), "id");

        api.get(token, url() + "/" + id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fechaNacimiento").value("2026-03-15"))
            .andExpect(jsonPath("$.fechaNacimientoEstimada").value(true));
    }

    @Test
    void listaLosActivosOrdenadosPorCaravana() throws Exception {
        crear(animal("C-3", "HEMBRA", "VACA"));
        crear(animal("A-1", "MACHO", "TORO"));
        crear(animal("B-2", "HEMBRA", "VAQUILLONA"));

        api.get(token, url())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].caravana").value("A-1"))
            .andExpect(jsonPath("$[2].caravana").value("C-3"));
    }

    // ---------- Validaciones ----------

    @Test
    void datosObligatoriosDevuelven400() throws Exception {
        api.post(token, url(), Map.of("caravana", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void fechaDeNacimientoFuturaDevuelve400() throws Exception {
        var body = animal("A-1", "HEMBRA", "TERNERA");
        body.put("fechaNacimiento", LocalDate.now().plusDays(1).toString());

        api.post(token, url(), body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("fechaNacimiento"));
    }

    @ParameterizedTest
    @CsvSource({"HEMBRA, TORO", "HEMBRA, NOVILLO", "MACHO, VACA", "MACHO, TERNERA"})
    void categoriaQueNoCorrespondeAlSexoDevuelve422(String sexo, String categoria) throws Exception {
        api.post(token, url(), animal("A-1", sexo, categoria))
            .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @CsvSource({
        "NOVILLO,   false, true",
        "NOVILLITO, false, true",
        "TORO,      true,  false",
        "TORITO,    true,  false",
        "TERNERO,   true,  true",
        "TERNERO,   false, false"
    })
    void laCategoriaDefineSiEstaCastrado(String categoria, boolean indicado, boolean esperado) throws Exception {
        var body = animal("A-1", "MACHO", categoria);
        body.put("castrado", indicado);

        api.post(token, url(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.castrado").value(esperado));
    }

    // ---------- Caravanas ----------

    @Test
    void caravanaRepetidaEnElEstablecimientoDevuelve409() throws Exception {
        crear(animal("A-100", "HEMBRA", "VACA"));

        api.post(token, url(), animal(" a-100", "MACHO", "TORO"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya hay un animal con la caravana A-100 en este establecimiento"));
    }

    @Test
    void laMismaCaravanaSePuedeUsarEnOtroEstablecimiento() throws Exception {
        crear(animal("A-100", "HEMBRA", "VACA"));
        String otro = crearEstablecimiento(token, "El Ombú");

        api.post(token, url(otro), animal("A-100", "HEMBRA", "VACA")).andExpect(status().isCreated());
    }

    @Test
    void laCaravanaElectronicaEsUnicaEnTodaLaOrganizacion() throws Exception {
        var primero = animal("A-1", "HEMBRA", "VACA");
        primero.put("caravanaElectronica", "032000111");
        crear(primero);
        String otro = crearEstablecimiento(token, "El Ombú");

        var segundo = animal("Z-9", "HEMBRA", "VACA");
        segundo.put("caravanaElectronica", "032000111");
        api.post(token, url(otro), segundo).andExpect(status().isConflict());
    }

    @Test
    void editarSinCambiarLaCaravanaNoChocaConsigoMismo() throws Exception {
        String id = crear(animal("A-1", "HEMBRA", "VAQUILLONA"));

        api.put(token, url() + "/" + id, animal("A-1", "HEMBRA", "VACA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoria").value("VACA"));
    }

    // ---------- Madre, padre, lote y rodeo ----------

    @Test
    void laMadreTieneQueSerHembraYElPadreMacho() throws Exception {
        String toro = crear(animal("T-1", "MACHO", "TORO"));
        String vaca = crear(animal("V-1", "HEMBRA", "VACA"));

        var conMadreMacho = animal("H-1", "HEMBRA", "TERNERA");
        conMadreMacho.put("madreId", toro);
        api.post(token, url(), conMadreMacho)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La madre tiene que ser hembra"));

        var conPadreHembra = animal("H-2", "HEMBRA", "TERNERA");
        conPadreHembra.put("padreId", vaca);
        api.post(token, url(), conPadreHembra)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El padre tiene que ser macho"));
    }

    @Test
    void unAnimalNoPuedeSerSuPropiaMadre() throws Exception {
        String id = crear(animal("V-1", "HEMBRA", "VACA"));
        var body = animal("V-1", "HEMBRA", "VACA");
        body.put("madreId", id);

        api.put(token, url() + "/" + id, body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void madreInexistenteDevuelve422() throws Exception {
        var body = animal("H-1", "HEMBRA", "TERNERA");
        body.put("madreId", UUID.randomUUID().toString());

        api.post(token, url(), body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La madre elegida no existe"));
    }

    @Test
    void laMadrePuedeEstarEnOtroEstablecimientoDeLaOrganizacion() throws Exception {
        String otro = crearEstablecimiento(token, "El Ombú");
        String madre = api.leer(api.post(token, url(otro), animal("V-1", "HEMBRA", "VACA")), "id");

        var cria = animal("H-1", "HEMBRA", "TERNERA");
        cria.put("madreId", madre);
        api.post(token, url(), cria)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.madre.nombre").value("V-1"));
    }

    @Test
    void elLoteYElRodeoTienenQueSerDelMismoEstablecimiento() throws Exception {
        String otro = crearEstablecimiento(token, "El Ombú");
        String loteDeOtro = api.leer(api.post(token, url(otro, "lotes"), Map.of("nombre", "Ajeno")), "id");

        var body = animal("A-1", "HEMBRA", "VACA");
        body.put("loteId", loteDeOtro);
        api.post(token, url(), body)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El lote elegido no existe en este establecimiento"));
    }

    @Test
    void lotesYRodeosMuestranCuantosAnimalesTienenYNoSePuedenBorrarConAnimales() throws Exception {
        String loteId = crearAgrupacion("lotes", "Recría");
        var body = animal("A-1", "HEMBRA", "VACA");
        body.put("loteId", loteId);
        crear(body);

        api.get(token, url(establecimientoId, "lotes"))
            .andExpect(jsonPath("$[0].cantidadAnimales").value(1));
        api.delete(token, url(establecimientoId, "lotes") + "/" + loteId)
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noSePuedeEliminarUnEstablecimientoConAnimales() throws Exception {
        crear(animal("A-1", "HEMBRA", "VACA"));
        crear(animal("A-2", "HEMBRA", "VACA"));

        api.delete(token, "/api/establecimientos/" + establecimientoId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message")
                .value("No se puede eliminar: todavía tiene 2 animales. Eliminalos primero."));
    }

    // ---------- Eliminar (carga equivocada) ----------

    @Test
    void eliminarBorraElAnimalSalvoQueTengaCrias() throws Exception {
        String madre = crear(animal("V-1", "HEMBRA", "VACA"));
        var cria = animal("H-1", "HEMBRA", "TERNERA");
        cria.put("madreId", madre);
        String criaId = crear(cria);

        api.delete(token, url() + "/" + madre).andExpect(status().isUnprocessableEntity());

        api.delete(token, url() + "/" + criaId).andExpect(status().isNoContent());
        api.delete(token, url() + "/" + madre).andExpect(status().isNoContent());
        api.get(token, url()).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- Permisos y aislamiento ----------

    @ParameterizedTest
    @CsvSource({"DUENO, 201", "ENCARGADO, 201", "VETERINARIO, 403", "SOCIO, 403"})
    void soloDuenoYEncargadoCarganAnimalesPeroTodosLosVen(Rol rol, int esperado) throws Exception {
        crear(animal("A-1", "HEMBRA", "VACA"));
        String tokenRol = tokenConRol(rol);

        api.get(tokenRol, url()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.post(tokenRol, url(), animal("A-2", "HEMBRA", "VACA")).andExpect(status().is(esperado));
    }

    @Test
    void otraOrganizacionNoPuedeVerNiTocarLosAnimales() throws Exception {
        String id = crear(animal("A-1", "HEMBRA", "VACA"));
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, url()).andExpect(status().isNotFound());
        api.post(tokenB, url(), animal("X-1", "HEMBRA", "VACA")).andExpect(status().isNotFound());
        api.get(tokenB, url() + "/" + id).andExpect(status().isNotFound());
        api.put(tokenB, url() + "/" + id, animal("X-1", "HEMBRA", "VACA")).andExpect(status().isNotFound());
        api.delete(tokenB, url() + "/" + id).andExpect(status().isNotFound());
    }

    @Test
    void unAnimalDeOtraOrganizacionNoSePuedeUsarComoMadre() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        String establecimientoB = crearEstablecimiento(tokenB, "Campo B");
        String vacaDeB = api.leer(api.post(tokenB, url(establecimientoB), animal("V-1", "HEMBRA", "VACA")), "id");

        var cria = animal("H-1", "HEMBRA", "TERNERA");
        cria.put("madreId", vacaDeB);
        api.post(token, url(), cria)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La madre elegida no existe"));
    }

    // ---------- Helpers ----------

    private String url() {
        return url(establecimientoId);
    }

    private static String url(String establecimiento) {
        return url(establecimiento, "animales");
    }

    private static String url(String establecimiento, String recurso) {
        return "/api/establecimientos/" + establecimiento + "/" + recurso;
    }

    private static Map<String, Object> animal(String caravana, String sexo, String categoria) {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", sexo);
        body.put("categoria", categoria);
        return body;
    }

    private String crear(Map<String, Object> body) throws Exception {
        return api.leer(api.post(token, url(), body).andExpect(status().isCreated()), "id");
    }

    private String crearAgrupacion(String tipo, String nombre) throws Exception {
        return api.leer(api.post(token, url(establecimientoId, tipo), Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    /** Usuario nuevo de la misma organización, agregado al establecimiento con ese rol (como en MembresiaControllerTest). */
    private String tokenConRol(Rol rol) throws Exception {
        if (rol == Rol.DUENO) return token;
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, url(establecimientoId, "miembros"), Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
