package com.innovax.ganadera.plan;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;
import com.innovax.ganadera.support.FakeEmailSender;

/**
 * Issue #35: planes, prueba gratis y límites. Las cuentas de los tests arrancan con prueba sin
 * límites (application-test.yml); acá cada test les pone el plan que necesita, con un plan "TEST"
 * de límites chicos para no tener que cargar 200 animales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanControllerTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeEmailSender emails;

    private ApiTestClient api;
    private String token;
    private String organizacionId;
    private String establecimientoId;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        emails.limpiar();
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        establecimientoId = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
        // 3 animales, 1 establecimiento, 2 usuarios
        jdbc.update("""
            INSERT INTO plan (codigo, nombre, orden, max_animales, max_establecimientos, max_usuarios)
            VALUES ('TEST', 'Test', 0, 3, 1, 2)""");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Prueba gratis ----------

    @Test
    void unaCuentaNuevaArrancaConLaPruebaGratis() throws Exception {
        // Solo los planes reales: el de los tests (orden 0) iría primero en la lista
        jdbc.update("DELETE FROM plan WHERE codigo = 'TEST'");

        api.get(token, "/api/plan")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enPrueba").value(true))
            .andExpect(jsonPath("$.diasDePrueba").value(30))
            .andExpect(jsonPath("$.pruebaHasta").value(HOY.plusDays(29).toString()))
            // Durante la prueba rige el plan de prueba (EMPRESA en los tests); el contratado es Inicial
            .andExpect(jsonPath("$.plan.codigo").value("EMPRESA"))
            .andExpect(jsonPath("$.contratado.codigo").value("INICIAL"))
            .andExpect(jsonPath("$.uso.establecimientos").value(1))
            .andExpect(jsonPath("$.uso.usuarios").value(1))
            .andExpect(jsonPath("$.planes[0].codigo").value("INICIAL"))
            .andExpect(jsonPath("$.planes[0].maxAnimales").value(200))
            .andExpect(jsonPath("$.planes[0].maxEstablecimientos").value(1))
            .andExpect(jsonPath("$.planes[0].maxUsuarios").value(2))
            .andExpect(jsonPath("$.planes[1].maxAnimales").value(1000))
            .andExpect(jsonPath("$.planes[2].codigo").value("EMPRESA"))
            .andExpect(jsonPath("$.planes[2].maxAnimales").doesNotExist());
    }

    @Test
    void alTerminarLaPruebaRigeElPlanContratado() throws Exception {
        plan("INICIAL", HOY.minusDays(1));

        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.enPrueba").value(false))
            .andExpect(jsonPath("$.diasDePrueba").value(0))
            .andExpect(jsonPath("$.plan.codigo").value("INICIAL"));
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"));
    }

    @Test
    void elUltimoDiaDePruebaTodaviaCuenta() throws Exception {
        plan("INICIAL", HOY);

        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.enPrueba").value(true))
            .andExpect(jsonPath("$.diasDePrueba").value(1));
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")).andExpect(status().isCreated());
    }

    @Test
    void siContratoUnPlanMayorQueElDePruebaRigeElContratado() throws Exception {
        // Plan de prueba en los tests: EMPRESA. Con TEST (menor) contratado, en prueba rige EMPRESA
        plan("TEST", HOY.plusDays(5));
        api.get(token, "/api/plan").andExpect(jsonPath("$.plan.codigo").value("EMPRESA"));
    }

    // ---------- Animales ----------

    @Test
    void noSePuedeCargarUnAnimalMasQueElLimite() throws Exception {
        plan("TEST", null);
        for (int i = 1; i <= 3; i++) {
            api.post(token, animales(), animal("A-" + i)).andExpect(status().isCreated());
        }

        api.post(token, animales(), animal("A-4"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"))
            .andExpect(jsonPath("$.message", containsString("límite de 3 animales del plan Test")));
        api.get(token, "/api/plan").andExpect(jsonPath("$.uso.animales").value(3));
    }

    @Test
    void losAnimalesVendidosNoCuentan() throws Exception {
        plan("TEST", null);
        for (int i = 1; i <= 3; i++) api.post(token, animales(), animal("A-" + i));
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE caravana = 'A-1'");

        api.post(token, animales(), animal("A-4")).andExpect(status().isCreated());
    }

    @Test
    void laImportacionNoPuedePasarseDelLimiteNiEnLaVistaPrevia() throws Exception {
        plan("TEST", null);
        api.post(token, animales(), animal("A-1"));
        List<Map<String, String>> filas = new ArrayList<>();
        for (int i = 1; i <= 3; i++) filas.add(Map.of("caravana", "I-" + i, "categoria", "Vaca"));

        api.post(token, animales() + "/importar?confirmar=false", Map.of("filas", filas))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"))
            .andExpect(jsonPath("$.message", containsString("te quedan 2 lugares")));
        api.post(token, animales() + "/importar?confirmar=true", Map.of("filas", filas))
            .andExpect(status().isUnprocessableEntity());
        api.post(token, animales() + "/importar?confirmar=true", Map.of("filas", filas.subList(0, 2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creados").value(2));
    }

    // ---------- Establecimientos ----------

    @Test
    void noSePuedeCrearUnEstablecimientoMasQueElLimite() throws Exception {
        plan("TEST", null);

        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"))
            .andExpect(jsonPath("$.message", containsString("permite 1 establecimiento.")));
    }

    @Test
    void unEstablecimientoEliminadoLiberaElLugar() throws Exception {
        plan("TEST", null);
        api.delete(token, "/api/establecimientos/" + establecimientoId).andExpect(status().isNoContent());

        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")).andExpect(status().isCreated());
    }

    // ---------- Usuarios ----------

    @Test
    void lasInvitacionesPendientesOcupanLugarDeUsuario() throws Exception {
        plan("TEST", null); // 2 usuarios: el dueño + 1

        api.post(token, invitaciones(), Map.of("email", "uno@test.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
        api.post(token, invitaciones(), Map.of("email", "dos@test.com", "rol", "SOCIO"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"))
            .andExpect(jsonPath("$.message", containsString("contando las invitaciones pendientes")));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.uso.usuarios").value(1))
            .andExpect(jsonPath("$.uso.invitacionesPendientes").value(1));
    }

    @Test
    void revocarUnaInvitacionLiberaElLugar() throws Exception {
        plan("TEST", null);
        String id = api.leer(api.post(token, invitaciones(), Map.of("email", "uno@test.com", "rol", "SOCIO")), "id");
        api.delete(token, invitaciones() + "/" + id).andExpect(status().isNoContent());

        api.post(token, invitaciones(), Map.of("email", "dos@test.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
    }

    @Test
    void aceptarLaInvitacionUsaElLugarReservado() throws Exception {
        plan("TEST", null);
        api.post(token, invitaciones(), Map.of("email", "uno@test.com", "rol", "SOCIO"));

        api.post(null, "/api/invitaciones/" + emails.ultimoToken() + "/registro",
                Map.of("nombre", "Uno", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isCreated());
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.uso.usuarios").value(2))
            .andExpect(jsonPath("$.uso.invitacionesPendientes").value(0));
    }

    @Test
    void siElPlanBajoNoSePuedeAceptarCreandoUnaCuentaMas() throws Exception {
        api.post(token, invitaciones(), Map.of("email", "uno@test.com", "rol", "SOCIO"));
        String tokenInvitacion = emails.ultimoToken();
        // Se invitó con la prueba (sin límites); después pasó a un plan de 1 usuario
        jdbc.update("UPDATE plan SET max_usuarios = 1 WHERE codigo = 'TEST'");
        plan("TEST", null);

        api.post(null, "/api/invitaciones/" + tokenInvitacion + "/registro",
                Map.of("nombre", "Uno", "password", ApiTestClient.PASSWORD))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LIMITE_PLAN"));
    }

    @Test
    void sumarAUnUsuarioQueYaEsDeLaCuentaNoOcupaLugar() throws Exception {
        plan("TEST", null);
        api.post(token, invitaciones(), Map.of("email", "uno@test.com", "rol", "SOCIO"));
        api.post(null, "/api/invitaciones/" + emails.ultimoToken() + "/registro",
            Map.of("nombre", "Uno", "password", ApiTestClient.PASSWORD));
        // Ya están los 2 usuarios: no entra nadie nuevo...
        api.post(token, invitaciones(), Map.of("email", "dos@test.com", "rol", "SOCIO"))
            .andExpect(status().isUnprocessableEntity());
        // ...pero sí se puede sumar al que ya es usuario a otro establecimiento (acá, sin límite de establecimientos)
        jdbc.update("UPDATE plan SET max_establecimientos = NULL WHERE codigo = 'TEST'");
        String otro = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")), "id");
        api.post(token, "/api/establecimientos/" + otro + "/invitaciones", Map.of("email", "uno@test.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
    }

    // ---------- Aislamiento ----------

    @Test
    void cadaOrganizacionVeSuPlanYSuUso() throws Exception {
        api.post(token, animales(), animal("A-1"));
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, "/api/plan")
            .andExpect(jsonPath("$.uso.animales").value(0))
            .andExpect(jsonPath("$.uso.establecimientos").value(0));
        api.get(null, "/api/plan").andExpect(status().isUnauthorized());
    }

    // ---------- Ayudas ----------

    /** Plan contratado y fin de la prueba (null: sin prueba). */
    /** Sin prueba la cuenta tiene que estar paga para usarse: se la marca con pago manual. */
    private void plan(String codigo, LocalDate pruebaHasta) {
        jdbc.update("UPDATE organizacion SET plan_codigo = ?, prueba_hasta = ?, pago_manual_hasta = ? WHERE id = ?",
            codigo, pruebaHasta, HOY.plusYears(1), UUID.fromString(organizacionId));
    }

    private String animales() {
        return "/api/establecimientos/" + establecimientoId + "/animales";
    }

    private String invitaciones() {
        return "/api/establecimientos/" + establecimientoId + "/invitaciones";
    }

    private static Map<String, Object> animal(String caravana) {
        return Map.of("caravana", caravana, "sexo", "HEMBRA", "categoria", "VACA");
    }
}
