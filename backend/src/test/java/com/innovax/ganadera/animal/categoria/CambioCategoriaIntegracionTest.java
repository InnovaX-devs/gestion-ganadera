package com.innovax.ganadera.animal.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.common.tenant.TenantContext;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/**
 * Issue #16 de punta a punta: job diario sin usuario logueado, eventos que llaman al servicio,
 * cambios manuales y el historial por API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CambioCategoriaIntegracionTest {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CambioCategoriaJob job;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private TransactionTemplate transaccion;

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
        SecurityContextHolder.clearContext();
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Job diario ----------

    @Test
    void elJobCambiaPorEdadEnTodasLasOrganizacionesYDejaHistorial() throws Exception {
        String ternero = crear(token, establecimientoId, animal("T-1", "MACHO", "TERNERO", HOY.minusMonths(9)));
        String terneraChica = crear(token, establecimientoId, animal("H-1", "HEMBRA", "TERNERA", HOY.minusMonths(3)));
        String sinFecha = crear(token, establecimientoId, animal("H-2", "HEMBRA", "TERNERA", null));

        String tokenB = api.registrarCuenta("Organización B");
        String establecimientoB = crearEstablecimiento(tokenB, "Campo B");
        String novillito = crear(tokenB, establecimientoB, animal("N-1", "MACHO", "NOVILLITO", HOY.minusMonths(25)));

        assertThat(ejecutarJob()).isEqualTo(2);

        categoriaEs(token, establecimientoId, ternero, "TORITO");
        categoriaEs(token, establecimientoId, terneraChica, "TERNERA");
        categoriaEs(token, establecimientoId, sinFecha, "TERNERA");
        categoriaEs(tokenB, establecimientoB, novillito, "NOVILLO");

        api.get(token, historial(establecimientoId, ternero))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].categoriaAnterior").value("TERNERO"))
            .andExpect(jsonPath("$[0].categoriaNueva").value("TORITO"))
            .andExpect(jsonPath("$[0].motivo").value("EDAD"))
            .andExpect(jsonPath("$[0].fecha").value(HOY.toString()))
            .andExpect(jsonPath("$[0].automatico").value(true));
        api.get(token, historial(establecimientoId, terneraChica)).andExpect(jsonPath("$.length()").value(0));

        // Cada fila quedó en la organización del animal
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cambio_categoria WHERE organizacion_id = ?",
            Long.class, organizacionId(token))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cambio_categoria WHERE organizacion_id = ?",
            Long.class, organizacionId(tokenB))).isEqualTo(1);
    }

    @Test
    void correrElJobDosVecesNoDuplicaCambios() throws Exception {
        crear(token, establecimientoId, animal("T-1", "MACHO", "TERNERO", HOY.minusMonths(9)));

        assertThat(ejecutarJob()).isEqualTo(1);
        assertThat(ejecutarJob()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cambio_categoria", Long.class)).isEqualTo(1);
    }

    @Test
    void elJobNoTocaAnimalesInactivosNiDeEstablecimientosDadosDeBaja() throws Exception {
        String vendido = crear(token, establecimientoId, animal("T-1", "MACHO", "TERNERO", HOY.minusMonths(9)));
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendido));

        String otroCampo = crearEstablecimiento(token, "Campo cerrado");
        crear(token, otroCampo, animal("T-2", "MACHO", "TERNERO", HOY.minusMonths(9)));
        jdbc.update("UPDATE establecimiento SET activo = FALSE WHERE id = ?", UUID.fromString(otroCampo));

        assertThat(ejecutarJob()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cambio_categoria", Long.class)).isZero();
    }

    // ---------- Eventos ----------

    @Test
    void unEventoDeDesteteRecalculaLaCategoria() throws Exception {
        var body = animal("T-1", "MACHO", "TERNERO", HOY.minusMonths(6));
        body.put("castrado", true);
        String ternero = crear(token, establecimientoId, body);

        // Así lo va a llamar el módulo de eventos, dentro de su transacción
        UUID organizacion = organizacionId(token);
        TenantContext.comoOrganizacion(organizacion, () -> transaccion.execute(estado ->
            categoriaService.aplicar(animalRepository.findById(UUID.fromString(ternero)).orElseThrow(),
                new Hecho.Destete(HOY.minusDays(1)))));

        api.get(token, url(establecimientoId) + "/" + ternero)
            .andExpect(jsonPath("$.categoria").value("NOVILLITO"))
            .andExpect(jsonPath("$.castrado").value(true));
        api.get(token, historial(establecimientoId, ternero))
            .andExpect(jsonPath("$[0].motivo").value("DESTETE"))
            .andExpect(jsonPath("$[0].fecha").value(HOY.minusDays(1).toString()));
    }

    // ---------- Cambios manuales ----------

    @Test
    void cambiarLaCategoriaAManoQuedaEnElHistorial() throws Exception {
        String id = crear(token, establecimientoId, animal("V-1", "HEMBRA", "VAQUILLONA", null));

        api.put(token, url(establecimientoId) + "/" + id, animal("V-1", "HEMBRA", "VACA", null))
            .andExpect(status().isOk());
        // Editar otra cosa sin cambiar la categoría no agrega filas
        var sinCambio = animal("V-1", "HEMBRA", "VACA", null);
        sinCambio.put("raza", "Hereford");
        api.put(token, url(establecimientoId) + "/" + id, sinCambio).andExpect(status().isOk());

        api.get(token, historial(establecimientoId, id))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].categoriaAnterior").value("VAQUILLONA"))
            .andExpect(jsonPath("$[0].categoriaNueva").value("VACA"))
            .andExpect(jsonPath("$[0].motivo").value("MANUAL"))
            .andExpect(jsonPath("$[0].automatico").value(false));
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM cambio_categoria", UUID.class))
            .isEqualTo(UUID.fromString(api.leer(api.get(token, "/api/auth/me"), "id")));
    }

    @Test
    void borrarUnAnimalCargadoPorErrorBorraSuHistorial() throws Exception {
        String id = crear(token, establecimientoId, animal("V-1", "HEMBRA", "VAQUILLONA", null));
        api.put(token, url(establecimientoId) + "/" + id, animal("V-1", "HEMBRA", "VACA", null))
            .andExpect(status().isOk());

        api.delete(token, url(establecimientoId) + "/" + id).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cambio_categoria", Long.class)).isZero();
    }

    // ---------- Aislamiento ----------

    @Test
    void otraOrganizacionNoPuedeVerElHistorial() throws Exception {
        String id = crear(token, establecimientoId, animal("T-1", "MACHO", "TERNERO", HOY.minusMonths(9)));
        ejecutarJob();
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, historial(establecimientoId, id)).andExpect(status().isNotFound());
    }

    @Test
    void unAnimalDeOtroEstablecimientoDevuelve404() throws Exception {
        String otroCampo = crearEstablecimiento(token, "Otro campo");
        String id = crear(token, otroCampo, animal("T-1", "MACHO", "TERNERO", null));

        api.get(token, historial(establecimientoId, id)).andExpect(status().isNotFound());
    }

    // ---------- Helpers ----------

    /** Como en el scheduler: sin usuario logueado en el hilo. */
    private int ejecutarJob() {
        SecurityContextHolder.clearContext();
        return job.ejecutar(HOY);
    }

    private void categoriaEs(String tokenUsuario, String establecimiento, String id, String categoria)
        throws Exception {
        api.get(tokenUsuario, url(establecimiento) + "/" + id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoria").value(categoria));
    }

    private UUID organizacionId(String tokenUsuario) throws Exception {
        return UUID.fromString(api.leer(api.get(tokenUsuario, "/api/auth/me"), "organizacionId"));
    }

    private static String url(String establecimiento) {
        return "/api/establecimientos/" + establecimiento + "/animales";
    }

    private static String historial(String establecimiento, String animalId) {
        return url(establecimiento) + "/" + animalId + "/cambios-categoria";
    }

    private static Map<String, Object> animal(String caravana, String sexo, String categoria, LocalDate nacimiento) {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", sexo);
        body.put("categoria", categoria);
        if (nacimiento != null) body.put("fechaNacimiento", nacimiento.toString());
        return body;
    }

    private String crear(String tokenUsuario, String establecimiento, Map<String, Object> body) throws Exception {
        return api.leer(api.post(tokenUsuario, url(establecimiento), body).andExpect(status().isCreated()), "id");
    }

    private String crearEstablecimiento(String tokenUsuario, String nombre) throws Exception {
        return api.leer(api.post(tokenUsuario, "/api/establecimientos", Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }
}
