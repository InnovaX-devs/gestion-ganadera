package com.innovax.ganadera.adjunto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #43: fotos y archivos adjuntos a eventos (observaciones) y a animales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdjuntoControllerTest {

    private static final String HOY = LocalDate.now().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdjuntosProperties propiedades;

    private ApiTestClient api;
    private String token;
    private String establecimientoId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        vaciarAlmacen();
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        establecimientoId = crearEstablecimiento(token, "La Esperanza");
    }

    @AfterEach
    void limpiar() throws Exception {
        DbCleaner.limpiar(jdbc);
        vaciarAlmacen();
    }

    // ---------- Eventos ----------

    @Test
    void laFotoDeUnaObservacionApareceEnElEventoYSePuedeBajar() throws Exception {
        String vaca = crearAnimal("V-1");
        String operacion = registrarObservacion(token, List.of(vaca));
        byte[] foto = jpeg(2000);

        String adjuntoId = api.leer(subirAEvento(token, operacion, "rengo.jpg", "image/jpeg", foto)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("rengo.jpg"))
            .andExpect(jsonPath("$.tipoContenido").value("image/jpeg"))
            .andExpect(jsonPath("$.tamanio").value(2000))
            .andExpect(jsonPath("$.createdAt").isNotEmpty()), "id");

        api.get(token, eventos() + "?animalId=" + vaca)
            .andExpect(jsonPath("$.contenido[0].adjuntos.length()").value(1))
            .andExpect(jsonPath("$.contenido[0].adjuntos[0].id").value(adjuntoId))
            .andExpect(jsonPath("$.contenido[0].adjuntos[0].nombre").value("rengo.jpg"));

        var descarga = api.get(token, adjuntos(adjuntoId))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
            .andReturn().getResponse();
        assertArrayEquals(foto, descarga.getContentAsByteArray());
    }

    @Test
    void siNoTieneArchivosLaListaViajaVacia() throws Exception {
        registrarObservacion(token, List.of());

        api.get(token, eventos())
            .andExpect(jsonPath("$.contenido[0].adjuntos").isArray())
            .andExpect(jsonPath("$.contenido[0].adjuntos.length()").value(0));
    }

    @Test
    void unaFotoDeUnEventoGrupalSeVeEnTodosLosEventosDeLaOperacion() throws Exception {
        String a1 = crearAnimal("V-1");
        String a2 = crearAnimal("V-2");
        String operacion = registrarObservacion(token, List.of(a1, a2));

        subirAEvento(token, operacion, "rodeo.png", "image/png", png(500)).andExpect(status().isCreated());

        for (String animal : List.of(a1, a2)) {
            api.get(token, eventos() + "?animalId=" + animal)
                .andExpect(jsonPath("$.contenido[0].adjuntos[0].nombre").value("rodeo.png"));
        }
        api.get(token, eventos()).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.contenido[0].adjuntos.length()").value(1))
            .andExpect(jsonPath("$.contenido[1].adjuntos.length()").value(1));
    }

    @Test
    void tambienSePuedeAdjuntarUnPdf() throws Exception {
        String operacion = registrarObservacion(token, List.of());

        subirAEvento(token, operacion, "informe.pdf", "application/pdf", pdf(300))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipoContenido").value("application/pdf"));
    }

    @Test
    void siSeAnulaElEventoSusArchivosDejanDeVerse() throws Exception {
        String vaca = crearAnimal("V-1");
        var evento = api.post(token, eventos(), observacion(List.of(vaca), UUID.randomUUID().toString()))
            .andExpect(status().isCreated());
        String operacion = api.leer(evento, "operacionId");
        String eventoId = objectMapper.readTree(evento.andReturn().getResponse().getContentAsString())
            .get("eventos").get(0).get("id").asText();
        String adjuntoId = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100)), "id");

        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());

        api.get(token, adjuntos(adjuntoId)).andExpect(status().isNotFound());
        subirAEvento(token, operacion, "b.jpg", "image/jpeg", jpeg(100)).andExpect(status().isNotFound());
    }

    @Test
    void eliminarUnArchivoLoSacaDelEventoYDelAlmacen() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        String adjuntoId = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100)), "id");
        Path archivo = archivoEnElAlmacen(adjuntoId);
        assertTrue(Files.exists(archivo));

        api.delete(token, adjuntos(adjuntoId)).andExpect(status().isNoContent());

        assertFalse(Files.exists(archivo));
        api.get(token, adjuntos(adjuntoId)).andExpect(status().isNotFound());
        api.get(token, eventos()).andExpect(jsonPath("$.contenido[0].adjuntos.length()").value(0));
    }

    @Test
    void unaOperacionInexistenteDevuelve404() throws Exception {
        subirAEvento(token, UUID.randomUUID().toString(), "a.jpg", "image/jpeg", jpeg(100))
            .andExpect(status().isNotFound());
    }

    // ---------- Validaciones ----------

    @Test
    void elTipoSeDetectaPorElContenidoNoPorElNombreNiElContentType() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        byte[] ejecutable = "MZ\u0090\u0000 esto no es una foto".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        subirAEvento(token, operacion, "foto.jpg", "image/jpeg", ejecutable)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Solo se pueden adjuntar fotos (JPG, PNG, WebP o HEIC) y archivos PDF"));

        // Una foto con un nombre raro igual se acepta, y se la reconoce como lo que es
        subirAEvento(token, operacion, "sin-extension", "application/octet-stream", jpeg(100))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipoContenido").value("image/jpeg"));
    }

    @Test
    void rechazaArchivosVaciosYMasGrandesQueElMaximo() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        long maximo = propiedades.tamanioMaximo().toBytes();

        subirAEvento(token, operacion, "vacio.jpg", "image/jpeg", new byte[0])
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Elegí un archivo para adjuntar"));
        subirAEvento(token, operacion, "grande.jpg", "image/jpeg", jpeg((int) maximo + 1))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("El archivo supera el máximo de 1 MB"));
        subirAEvento(token, operacion, "justo.jpg", "image/jpeg", jpeg((int) maximo))
            .andExpect(status().isCreated());
    }

    @Test
    void nombresConRutasOCaracteresDeControlSeLimpian() throws Exception {
        String operacion = registrarObservacion(token, List.of());

        subirAEvento(token, operacion, "../../etc/passwd\u0000.jpg", "image/jpeg", jpeg(100))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("passwd.jpg"));
        subirAEvento(token, operacion, "C:\\fotos\\vaca.jpg", "image/jpeg", jpeg(100))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombre").value("vaca.jpg"));
    }

    @Test
    void hayUnMaximoDeArchivosPorOperacion() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        for (int i = 0; i < propiedades.maximoPorElemento(); i++) {
            subirAEvento(token, operacion, "f" + i + ".jpg", "image/jpeg", jpeg(100)).andExpect(status().isCreated());
        }

        subirAEvento(token, operacion, "una-mas.jpg", "image/jpeg", jpeg(100))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Se pueden adjuntar hasta " + propiedades.maximoPorElemento() + " archivos"));
    }

    @Test
    void sinLaParteArchivoDevuelve400() throws Exception {
        String operacion = registrarObservacion(token, List.of());

        api.postArchivo(token, eventos() + "/operaciones/" + operacion + "/adjuntos",
                new MockMultipartFile("otra", "a.jpg", "image/jpeg", jpeg(100)), Map.of())
            .andExpect(status().isBadRequest());
    }

    // ---------- Reintentos (app offline) ----------

    @Test
    void reenviarLaMismaCargaNoDuplicaElArchivo() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        String cargaId = UUID.randomUUID().toString();

        String primero = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100), cargaId)
            .andExpect(status().isCreated()), "id");
        String segundo = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100), cargaId)
            .andExpect(status().isCreated()), "id");

        assertEquals(primero, segundo);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM adjunto", Integer.class));
        assertEquals(1, archivosEnElAlmacen());
    }

    @Test
    void unaCargaReutilizadaEnOtroLugarSeRechaza() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        String otra = registrarObservacion(token, List.of());
        String cargaId = UUID.randomUUID().toString();
        subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100), cargaId).andExpect(status().isCreated());

        subirAEvento(token, otra, "a.jpg", "image/jpeg", jpeg(100), cargaId)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Ese identificador de carga ya está en uso"));
    }

    // ---------- Animales ----------

    @Test
    void sePuedenAdjuntarArchivosDirectamenteAUnAnimal() throws Exception {
        String vaca = crearAnimal("V-1");
        byte[] foto = png(800);

        String id = api.leer(subirAAnimal(token, vaca, "ficha.png", "image/png", foto)
            .andExpect(status().isCreated()), "id");
        subirAAnimal(token, vaca, "informe.pdf", "application/pdf", pdf(100)).andExpect(status().isCreated());

        api.get(token, animales(vaca) + "/adjuntos")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            // Del más nuevo al más viejo
            .andExpect(jsonPath("$[0].nombre").value("informe.pdf"))
            .andExpect(jsonPath("$[1].nombre").value("ficha.png"));
        var descarga = api.get(token, adjuntos(id)).andExpect(status().isOk()).andReturn().getResponse();
        assertArrayEquals(foto, descarga.getContentAsByteArray());

        api.delete(token, adjuntos(id)).andExpect(status().isNoContent());
        api.get(token, animales(vaca) + "/adjuntos").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void losArchivosDeUnAnimalDeOtroEstablecimientoNoSeVenDesdeEste() throws Exception {
        String vaca = crearAnimal("V-1");
        String id = api.leer(subirAAnimal(token, vaca, "ficha.png", "image/png", png(100)), "id");
        String otro = crearEstablecimiento(token, "El Ombú");

        api.get(token, url(otro, "animales/" + vaca + "/adjuntos")).andExpect(status().isNotFound());
        api.get(token, url(otro, "adjuntos/" + id)).andExpect(status().isNotFound());
        subirAAnimal(token, otro, vaca, "x.png", "image/png", png(100)).andExpect(status().isNotFound());
    }

    @Test
    void borrarUnAnimalCargadoPorErrorBorraSusArchivosYLosDeSusEventosAnulados() throws Exception {
        String vaca = crearAnimal("V-1");
        String delAnimal = api.leer(subirAAnimal(token, vaca, "ficha.png", "image/png", png(100)), "id");
        var evento = api.post(token, eventos(), observacion(List.of(vaca), UUID.randomUUID().toString()))
            .andExpect(status().isCreated());
        String operacion = api.leer(evento, "operacionId");
        String eventoId = objectMapper.readTree(evento.andReturn().getResponse().getContentAsString())
            .get("eventos").get(0).get("id").asText();
        String delEvento = api.leer(subirAEvento(token, operacion, "foto.jpg", "image/jpeg", jpeg(100)), "id");
        Path archivoAnimal = archivoEnElAlmacen(delAnimal);
        Path archivoEvento = archivoEnElAlmacen(delEvento);

        api.delete(token, eventos() + "/" + eventoId).andExpect(status().isNoContent());
        api.delete(token, animales(vaca)).andExpect(status().isNoContent());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM adjunto", Integer.class));
        assertFalse(Files.exists(archivoAnimal));
        assertFalse(Files.exists(archivoEvento));
    }

    @Test
    void siLaOperacionTodaviaTieneOtrosEventosSusArchivosSeConservanAlBorrarUnAnimal() throws Exception {
        String a1 = crearAnimal("V-1");
        String a2 = crearAnimal("V-2");
        String operacion = registrarObservacion(token, List.of(a1, a2));
        subirAEvento(token, operacion, "foto.jpg", "image/jpeg", jpeg(100)).andExpect(status().isCreated());

        // Se anula el evento de V-1 (el de V-2 sigue) y se borra a V-1
        String eventoDeA1 = jdbc.queryForObject("SELECT id FROM evento WHERE operacion_id = ? AND animal_id = ?",
            String.class, UUID.fromString(operacion), UUID.fromString(a1));
        api.delete(token, eventos() + "/" + eventoDeA1).andExpect(status().isNoContent());
        api.delete(token, animales(a1)).andExpect(status().isNoContent());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM adjunto", Integer.class));
        api.get(token, eventos() + "?animalId=" + a2)
            .andExpect(jsonPath("$.contenido[0].adjuntos.length()").value(1));
    }

    // ---------- Permisos y aislamiento ----------

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 201", "SOCIO, 403"})
    void alEventoLoAdjuntanQuienesPuedenRegistrarEseTipoYTodosLoVen(Rol rol, int esperado) throws Exception {
        String operacion = registrarObservacion(token, List.of());
        String delDueno = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100)), "id");
        String tokenRol = tokenConRol(rol);

        subirAEvento(tokenRol, operacion, "b.jpg", "image/jpeg", jpeg(100)).andExpect(status().is(esperado));
        api.get(tokenRol, adjuntos(delDueno)).andExpect(status().isOk());
        api.get(tokenRol, eventos()).andExpect(jsonPath("$.contenido[0].adjuntos[0].id").value(delDueno));
    }

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 403", "SOCIO, 403"})
    void alAnimalLoAdjuntanQuienesPuedenGestionarAnimales(Rol rol, int esperado) throws Exception {
        String vaca = crearAnimal("V-1");
        String delDueno = api.leer(subirAAnimal(token, vaca, "a.png", "image/png", png(100)), "id");
        String tokenRol = tokenConRol(rol);

        subirAAnimal(tokenRol, vaca, "b.png", "image/png", png(100)).andExpect(status().is(esperado));
        api.get(tokenRol, animales(vaca) + "/adjuntos").andExpect(status().isOk());
        api.delete(tokenRol, adjuntos(delDueno)).andExpect(status().is(esperado == 201 ? 204 : 403));
    }

    @Test
    void elVeterinarioNoPuedeAdjuntarAEventosDeManejo() throws Exception {
        String vaca = crearAnimal("V-1");
        var pesaje = observacion(List.of(vaca), UUID.randomUUID().toString());
        pesaje.put("tipo", "PESAJE");
        pesaje.put("datos", Map.of("kg", 350));
        String operacion = api.leer(api.post(token, eventos(), pesaje).andExpect(status().isCreated()), "operacionId");

        subirAEvento(tokenConRol(Rol.VETERINARIO), operacion, "a.jpg", "image/jpeg", jpeg(100))
            .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        String operacion = registrarObservacion(token, List.of());
        String id = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100)), "id");

        subirAEvento(null, operacion, "b.jpg", "image/jpeg", jpeg(100)).andExpect(status().isUnauthorized());
        api.get(null, adjuntos(id)).andExpect(status().isUnauthorized());
    }

    @Test
    void otraOrganizacionNoPuedeVerSubirNiEliminar() throws Exception {
        String vaca = crearAnimal("V-1");
        String operacion = registrarObservacion(token, List.of(vaca));
        String delEvento = api.leer(subirAEvento(token, operacion, "a.jpg", "image/jpeg", jpeg(100)), "id");
        String delAnimal = api.leer(subirAAnimal(token, vaca, "b.png", "image/png", png(100)), "id");
        String tokenB = api.registrarCuenta("Organización B");

        // Con el establecimiento de A, y con uno propio de B: en los dos casos A es invisible
        String propioDeB = crearEstablecimiento(tokenB, "Campo B");
        for (String est : List.of(establecimientoId, propioDeB)) {
            api.get(tokenB, url(est, "adjuntos/" + delEvento)).andExpect(status().isNotFound());
            api.get(tokenB, url(est, "adjuntos/" + delAnimal)).andExpect(status().isNotFound());
            api.delete(tokenB, url(est, "adjuntos/" + delEvento)).andExpect(status().isNotFound());
            api.get(tokenB, url(est, "animales/" + vaca + "/adjuntos")).andExpect(status().isNotFound());
        }
        subirAEvento(tokenB, establecimientoId, operacion, "x.jpg", "image/jpeg", jpeg(100), null)
            .andExpect(status().isNotFound());
        subirAEvento(tokenB, propioDeB, operacion, "x.jpg", "image/jpeg", jpeg(100), null)
            .andExpect(status().isNotFound());

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM adjunto", Integer.class));
        api.get(token, adjuntos(delEvento)).andExpect(status().isOk());
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url(establecimientoId, "eventos");
    }

    private String adjuntos(String id) {
        return url(establecimientoId, "adjuntos/" + id);
    }

    private String animales(String id) {
        return url(establecimientoId, "animales/" + id);
    }

    private static String url(String establecimiento, String recurso) {
        return "/api/establecimientos/" + establecimiento + "/" + recurso;
    }

    private ResultActions subirAEvento(String tokenUsuario, String operacion, String nombre, String tipo, byte[] contenido)
        throws Exception {
        return subirAEvento(tokenUsuario, establecimientoId, operacion, nombre, tipo, contenido, null);
    }

    private ResultActions subirAEvento(String tokenUsuario, String operacion, String nombre, String tipo,
                                       byte[] contenido, String cargaId) throws Exception {
        return subirAEvento(tokenUsuario, establecimientoId, operacion, nombre, tipo, contenido, cargaId);
    }

    private ResultActions subirAEvento(String tokenUsuario, String establecimiento, String operacion, String nombre,
                                       String tipo, byte[] contenido, String cargaId) throws Exception {
        Map<String, String> params = cargaId == null ? Map.of() : Map.of("cargaId", cargaId);
        return api.postArchivo(tokenUsuario,
            url(establecimiento, "eventos/operaciones/" + operacion + "/adjuntos"),
            new MockMultipartFile("archivo", nombre, tipo, contenido), params);
    }

    private ResultActions subirAAnimal(String tokenUsuario, String animal, String nombre, String tipo, byte[] contenido)
        throws Exception {
        return subirAAnimal(tokenUsuario, establecimientoId, animal, nombre, tipo, contenido);
    }

    private ResultActions subirAAnimal(String tokenUsuario, String establecimiento, String animal, String nombre,
                                       String tipo, byte[] contenido) throws Exception {
        return api.postArchivo(tokenUsuario, url(establecimiento, "animales/" + animal + "/adjuntos"),
            new MockMultipartFile("archivo", nombre, tipo, contenido), Map.of());
    }

    private static Map<String, Object> observacion(List<String> animalIds, String operacionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("operacionId", operacionId);
        body.put("tipo", "OBSERVACION");
        body.put("fecha", HOY);
        body.put("animalIds", animalIds);
        body.put("datos", Map.of("texto", "Nota con foto"));
        return body;
    }

    /** Registra una observación y devuelve su operacionId. */
    private String registrarObservacion(String tokenUsuario, List<String> animalIds) throws Exception {
        return api.leer(api.post(tokenUsuario, eventos(), observacion(animalIds, UUID.randomUUID().toString()))
            .andExpect(status().isCreated()), "operacionId");
    }

    private String crearAnimal(String caravana) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", "HEMBRA");
        body.put("categoria", "VACA");
        return api.leer(api.post(token, url(establecimientoId, "animales"), body).andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    /** Dónde quedó el archivo de ese adjunto, según la clave que guardó la base. */
    private Path archivoEnElAlmacen(String adjuntoId) {
        String clave = jdbc.queryForObject("SELECT clave FROM adjunto WHERE id = ?", String.class, UUID.fromString(adjuntoId));
        return Path.of(propiedades.directorio()).toAbsolutePath().normalize().resolve(clave);
    }

    /** Los archivos no se borran con DbCleaner: se vacía el directorio de pruebas a mano. */
    private void vaciarAlmacen() throws Exception {
        Path raiz = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
        if (!Files.exists(raiz)) {
            return;
        }
        try (var archivos = Files.walk(raiz)) {
            for (Path p : archivos.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!p.equals(raiz)) {
                    Files.delete(p);
                }
            }
        }
    }

    private long archivosEnElAlmacen() throws Exception {
        Path raiz = Path.of(propiedades.directorio()).toAbsolutePath().normalize();
        if (!Files.exists(raiz)) {
            return 0;
        }
        try (var archivos = Files.walk(raiz)) {
            return archivos.filter(Files::isRegularFile).count();
        }
    }

    /** Usuario nuevo de la organización agregado al establecimiento con ese rol. */
    private String tokenConRol(Rol rol) throws Exception {
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

    // ---------- Archivos de prueba: solo importan los primeros bytes ----------

    private static byte[] jpeg(int tamanio) {
        return conCabecera(tamanio, 0xFF, 0xD8, 0xFF, 0xE0);
    }

    private static byte[] png(int tamanio) {
        return conCabecera(tamanio, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private static byte[] pdf(int tamanio) {
        return conCabecera(tamanio, '%', 'P', 'D', 'F', '-', '1', '.', '7');
    }

    private static byte[] conCabecera(int tamanio, int... cabecera) {
        byte[] bytes = new byte[tamanio];
        Arrays.fill(bytes, (byte) 7);
        for (int i = 0; i < cabecera.length && i < tamanio; i++) {
            bytes[i] = (byte) cabecera[i];
        }
        return bytes;
    }
}
