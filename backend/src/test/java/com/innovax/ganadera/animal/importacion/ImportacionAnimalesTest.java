package com.innovax.ganadera.animal.importacion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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

/** Issue #31: importar animales desde una planilla. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacionAnimalesTest {

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

    // ---------- Vista previa ----------

    @Test
    void laVistaPreviaRevisaPeroNoGuardaNada() throws Exception {
        var filas = List.of(
            fila("V-1", "Hembra", "Vaca", "lote", "Recría"),
            fila("T-1", "Macho", "Toro"));

        api.post(token, url(false), Map.of("filas", filas))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.validas").value(2))
            .andExpect(jsonPath("$.creados").value(0))
            .andExpect(jsonPath("$.errores.length()").value(0))
            .andExpect(jsonPath("$.lotesNuevos[0]").value("Recría"));

        api.get(token, animales()).andExpect(jsonPath("$.total").value(0));
        api.get(token, base() + "/lotes").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void marcaLosErroresDeCadaFila() throws Exception {
        String futura = LocalDate.now().plusDays(5).toString();
        var filas = List.of(
            fila(null, "Hembra", "Vaca"),                                // 2: sin caravana
            fila("A-1", "Otro", "Vaca"),                                 // 3: sexo inválido
            fila("A-2", "Macho", "Vaca"),                                // 4: categoría de otro sexo
            fila("A-3", "Hembra", "Vaquita"),                            // 5: categoría inexistente
            fila("A-4", "Hembra", "Vaca", "fechaNacimiento", futura),    // 6: fecha futura
            fila("A-5", "Hembra", "Vaca", "fechaNacimiento", "31/02/2024"), // 7: fecha inválida
            fila("A-6", "Hembra", "Vaca"),                               // 8: ok
            fila("a-6 ", "Hembra", "Vaca"),                              // 9: repetida (se normaliza)
            fila("A-7", "Hembra", "Vaca", "castrado", "tal vez"));       // 10: sí/no inválido

        api.post(token, url(false), Map.of("filas", filas))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(9))
            .andExpect(jsonPath("$.validas").value(1))
            .andExpect(jsonPath("$.errores.length()").value(8))
            .andExpect(jsonPath("$.errores[0].fila").value(2))
            .andExpect(jsonPath("$.errores[0].campo").value("caravana"))
            .andExpect(jsonPath("$.errores[1].campo").value("sexo"))
            .andExpect(jsonPath("$.errores[2].mensaje").value("La categoría vaca no corresponde a un macho"))
            .andExpect(jsonPath("$.errores[3].campo").value("categoria"))
            .andExpect(jsonPath("$.errores[4].mensaje").value("La fecha de nacimiento no puede ser futura"))
            .andExpect(jsonPath("$.errores[5].campo").value("fechaNacimiento"))
            .andExpect(jsonPath("$.errores[6].fila").value(9))
            .andExpect(jsonPath("$.errores[6].mensaje").value("La caravana A-6 ya está en la fila 8"))
            .andExpect(jsonPath("$.errores[7].campo").value("castrado"));
    }

    @Test
    void avisaSiLaCaravanaYaEstaCargada() throws Exception {
        api.post(token, animales(), Map.of("caravana", "V-1", "sexo", "HEMBRA", "categoria", "VACA",
            "caravanaElectronica", "032000000000001")).andExpect(status().isCreated());

        var repetida = fila("v-1", "Hembra", "Vaca");
        var electronica = fila("V-2", "Hembra", "Vaca", "caravanaElectronica", "032000000000001");

        api.post(token, url(false), Map.of("filas", List.of(repetida, electronica)))
            .andExpect(jsonPath("$.errores[0].mensaje").value("Ya hay un animal con la caravana V-1 en este establecimiento"))
            .andExpect(jsonPath("$.errores[1].campo").value("caravanaElectronica"));
    }

    @Test
    void revisaMadreYPadre() throws Exception {
        var filas = List.of(
            fila("T-1", "Macho", "Toro"),
            fila("C-1", null, "Ternera", "madre", "T-1"),   // 3: la madre es macho
            fila("C-2", null, "Ternera", "padre", "X-99"),  // 4: no existe
            fila("C-3", null, "Ternera", "madre", "C-3"));  // 5: ella misma

        api.post(token, url(false), Map.of("filas", filas))
            .andExpect(jsonPath("$.validas").value(1))
            .andExpect(jsonPath("$.errores[0].mensaje").value("La madre (T-1) tiene que ser hembra"))
            .andExpect(jsonPath("$.errores[1].mensaje", containsString("No hay ningún animal con la caravana X-99")))
            .andExpect(jsonPath("$.errores[2].mensaje").value("Un animal no puede ser su propia madre"));
    }

    // ---------- Confirmar ----------

    @Test
    void confirmarGuardaTodoConLotesNuevosYGenealogia() throws Exception {
        String vacaCargada = api.leer(api.post(token, animales(),
            Map.of("caravana", "V-100", "sexo", "HEMBRA", "categoria", "VACA")), "id");
        String loteExistente = api.leer(api.post(token, base() + "/lotes", Map.of("nombre", "Recría")), "id");

        var cria = fila("C-1", null, "ternero", "madre", "V-1"); // la madre está más abajo en la planilla
        cria.put("padre", "t-1");
        cria.put("castrado", "Sí");
        cria.put("fechaNacimiento", "15/09/2025");
        cria.put("lote", "recria");                               // existe con otra escritura
        var vaca = fila("V-1", "H", "Vaca", "rodeo", "Cría");
        vaca.put("raza", " Angus ");
        vaca.put("fechaNacimiento", "2020-03-01");
        vaca.put("fechaNacimientoEstimada", "x");
        var toro = fila("T-1", "M", "Toro", "lote", "Toros");
        var hija = fila("C-2", null, "Ternera", "madre", "V-100");

        api.post(token, url(true), Map.of("filas", List.of(cria, vaca, toro, hija)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creados").value(4))
            .andExpect(jsonPath("$.lotesNuevos.length()").value(1))
            .andExpect(jsonPath("$.lotesNuevos[0]").value("Toros"))
            .andExpect(jsonPath("$.rodeosNuevos[0]").value("Cría"));

        String criaId = idDe("C-1");
        api.get(token, animales() + "/" + criaId)
            .andExpect(jsonPath("$.sexo").value("MACHO"))
            .andExpect(jsonPath("$.castrado").value(true))
            .andExpect(jsonPath("$.fechaNacimiento").value("2025-09-15"))
            .andExpect(jsonPath("$.madre.nombre").value("V-1"))
            .andExpect(jsonPath("$.padre.nombre").value("T-1"))
            .andExpect(jsonPath("$.lote.id").value(loteExistente));
        api.get(token, animales() + "/" + idDe("V-1"))
            .andExpect(jsonPath("$.raza").value("Angus"))
            .andExpect(jsonPath("$.fechaNacimientoEstimada").value(true))
            .andExpect(jsonPath("$.rodeo.nombre").value("Cría"));
        api.get(token, animales() + "/" + vacaCargada + "/crias")
            .andExpect(jsonPath("$[0].caravana").value("C-2"));
        api.get(token, base() + "/lotes").andExpect(jsonPath("$[*].nombre", hasItem("Toros")));
        api.get(token, animales()).andExpect(jsonPath("$.total").value(5));
    }

    @Test
    void conUnSoloErrorNoGuardaNingunAnimal() throws Exception {
        var filas = List.of(fila("V-1", "Hembra", "Vaca", "lote", "Nuevo"), fila("V-2", "Macho", "Vaca"));

        api.post(token, url(true), Map.of("filas", filas))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("La planilla tiene 1 error. Corregilos y volvé a cargarla."));

        api.get(token, animales()).andExpect(jsonPath("$.total").value(0));
        api.get(token, base() + "/lotes").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rechazaUnaPlanillaVaciaODemasiadoGrande() throws Exception {
        api.post(token, url(false), Map.of("filas", List.of())).andExpect(status().isBadRequest());

        List<Map<String, String>> muchas = new ArrayList<>();
        for (int i = 0; i < 2001; i++) muchas.add(fila("A-" + i, "Hembra", "Vaca"));
        api.post(token, url(false), Map.of("filas", muchas)).andExpect(status().isBadRequest());
    }

    // ---------- Permisos y aislamiento ----------

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 200", "VETERINARIO, 403", "SOCIO, 403"})
    void soloQuienGestionaAnimalesPuedeImportar(Rol rol, int esperado) throws Exception {
        api.post(tokenConRol(rol), url(true), Map.of("filas", List.of(fila("V-1", "Hembra", "Vaca", "lote", "Nuevo"))))
            .andExpect(status().is(esperado));
    }

    @Test
    void otraOrganizacionNoPuedeImportarEnEsteEstablecimiento() throws Exception {
        String tokenB = api.registrarCuenta("Organización B");
        api.post(tokenB, url(true), Map.of("filas", List.of(fila("V-1", "Hembra", "Vaca"))))
            .andExpect(status().isNotFound());
        api.get(token, animales()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void laMadreTieneQueSerDeEsteEstablecimiento() throws Exception {
        String otroEstablecimiento = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")), "id");
        api.post(token, "/api/establecimientos/" + otroEstablecimiento + "/animales",
            Map.of("caravana", "V-9", "sexo", "HEMBRA", "categoria", "VACA")).andExpect(status().isCreated());

        api.post(token, url(false), Map.of("filas", List.of(fila("C-1", null, "Ternera", "madre", "V-9"))))
            .andExpect(jsonPath("$.errores[0].campo").value("madre"));
    }

    // ---------- Ayudas ----------

    private String base() {
        return "/api/establecimientos/" + establecimientoId;
    }

    private String animales() {
        return base() + "/animales";
    }

    private String url(boolean confirmar) {
        return animales() + "/importar?confirmar=" + confirmar;
    }

    private static Map<String, String> fila(String caravana, String sexo, String categoria) {
        Map<String, String> f = new HashMap<>();
        f.put("caravana", caravana);
        f.put("sexo", sexo);
        f.put("categoria", categoria);
        return f;
    }

    private static Map<String, String> fila(String caravana, String sexo, String categoria, String campo, String valor) {
        Map<String, String> f = fila(caravana, sexo, categoria);
        f.put(campo, valor);
        return f;
    }

    private String idDe(String caravana) throws Exception {
        String json = api.get(token, animales() + "?q=" + caravana).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("contenido").get(0).get("id").asText();
    }

    private String tokenConRol(Rol rol) throws Exception {
        String organizacionId = api.leer(api.get(token, "/api/auth/me"), "organizacionId");
        String email = "miembro-" + UUID.randomUUID() + "@test.com";
        jdbc.update("INSERT INTO usuario (id, organizacion_id, email, password_hash, nombre) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), UUID.fromString(organizacionId), email,
            passwordEncoder.encode(ApiTestClient.PASSWORD), "Miembro");
        api.post(token, base() + "/miembros", Map.of("email", email, "rol", rol.name()))
            .andExpect(status().isCreated());
        return api.leer(api.post(null, "/api/auth/login", Map.of("email", email, "password", ApiTestClient.PASSWORD)),
            "accessToken");
    }
}
