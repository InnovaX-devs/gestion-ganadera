package com.innovax.ganadera.pesaje;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;

/** Issue #21: pesaje individual, grupal, carga en grilla y ganancia diaria de peso. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PesajeControllerTest {

    /** El "hoy" de la aplicación es el de Argentina (ver CategoriaConfig), no el del servidor. */
    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

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

    // ---------- Pesaje individual ----------

    @Test
    void registraElPesoDeUnAnimalYQuedaEnSuHistorial() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("kg", 412.5)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1))
            .andExpect(jsonPath("$.eventos[0].tipo").value("PESAJE"))
            .andExpect(jsonPath("$.eventos[0].datos.kg").value(412.5))
            .andExpect(jsonPath("$.eventos[0].datos.grupal").value(false));

        api.get(token, eventos() + "?animalId=" + vaca + "&tipo=PESAJE").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void validaElPeso() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("kg", 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.kg"));
        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("kg", 1501)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].message").value("El peso máximo es 1500 kg"));
        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Indicá el peso del animal, o el peso total y la cantidad pesada"));
    }

    @Test
    void unPesoIndividualNoSePuedeAplicarAVariosAnimales() throws Exception {
        String a = crearAnimal("V-1", "HEMBRA", "VACA");
        String b = crearAnimal("V-2", "HEMBRA", "VACA");

        api.post(token, eventos(), pesaje(HOY, List.of(a, b), Map.of("kg", 400)))
            .andExpect(status().isUnprocessableEntity());
        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void unNovillitoQuePesaLoSuficientePasaANovillo() throws Exception {
        String novillito = crearAnimal("N-1", "MACHO", "NOVILLITO");

        api.post(token, eventos(), pesaje(HOY, List.of(novillito), Map.of("kg", 420))).andExpect(status().isCreated());

        api.get(token, url("animales") + "/" + novillito).andExpect(jsonPath("$.categoria").value("NOVILLO"));
    }

    // ---------- Pesaje grupal ----------

    @Test
    void elPesajeGrupalDeUnLoteDaElPromedioACadaAnimal() throws Exception {
        String lote = api.leer(api.post(token, url("lotes"), Map.of("nombre", "Recría")), "id");
        String a1 = crearAnimalEn("N-1", "MACHO", "NOVILLITO", lote);
        crearAnimalEn("N-2", "MACHO", "NOVILLITO", lote);
        crearAnimalEn("N-3", "MACHO", "NOVILLITO", lote);

        var body = pesaje(HOY, null, Map.of("pesoTotal", 1300, "cantidad", 3));
        body.put("loteId", lote);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(3))
            .andExpect(jsonPath("$.eventos[0].datos.kg").value(433.33))
            .andExpect(jsonPath("$.eventos[0].datos.grupal").value(true))
            .andExpect(jsonPath("$.eventos[0].datos.pesoTotal").value(1300))
            .andExpect(jsonPath("$.eventos[0].datos.cantidad").value(3));

        // El promedio no dice cuánto pesa cada uno: no cambia categorías
        api.get(token, url("animales") + "/" + a1).andExpect(jsonPath("$.categoria").value("NOVILLITO"));
        api.get(token, url("pesajes") + "?animalId=" + a1)
            .andExpect(jsonPath("$.contenido[0].kg").value(433.33))
            .andExpect(jsonPath("$.contenido[0].grupal").value(true))
            .andExpect(jsonPath("$.contenido[0].cantidad").value(3));
    }

    @Test
    void elPesajeGrupalTambienSirveParaUnRodeoYParaAnimalesElegidos() throws Exception {
        String rodeo = api.leer(api.post(token, url("rodeos"), Map.of("nombre", "Cría")), "id");
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("caravana", "V-1");
        cuerpo.put("sexo", "HEMBRA");
        cuerpo.put("categoria", "VACA");
        cuerpo.put("rodeoId", rodeo);
        api.post(token, url("animales"), cuerpo).andExpect(status().isCreated());
        String suelta = crearAnimal("V-2", "HEMBRA", "VACA");
        String otra = crearAnimal("V-3", "HEMBRA", "VACA");

        var deRodeo = pesaje(HOY, null, Map.of("pesoTotal", 500, "cantidad", 1));
        deRodeo.put("rodeoId", rodeo);
        api.post(token, eventos(), deRodeo).andExpect(status().isCreated()).andExpect(jsonPath("$.cantidad").value(1));

        api.post(token, eventos(), pesaje(HOY, List.of(suelta, otra), Map.of("pesoTotal", 801, "cantidad", 2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2))
            .andExpect(jsonPath("$.eventos[0].datos.kg").value(400.5));
    }

    @Test
    void elPesajeGrupalNecesitaPesoTotalYCantidad() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("pesoTotal", 1000)))
            .andExpect(status().isUnprocessableEntity());
        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("pesoTotal", 1000, "cantidad", 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.cantidad"));
    }

    // ---------- Grilla ----------

    @Test
    void laGrillaRegistraUnPesajePorFilaComoUnaSolaOperacion() throws Exception {
        String a = crearAnimal("V-1", "HEMBRA", "VACA");
        crearAnimal("V-2", "HEMBRA", "VACA");
        String novillito = crearAnimal("N-1", "MACHO", "NOVILLITO");

        String operacion = api.leer(api.post(token, pesajes() + "/grilla", grilla(HOY,
                fila(" v-1 ", 412.5), fila("V-2", 380), fila("N-1", 405)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(3))
            .andExpect(jsonPath("$.eventos[0].tipo").value("PESAJE")), "operacionId");

        api.get(token, eventos() + "?tipo=PESAJE").andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.contenido[0].operacionId").value(operacion));
        api.get(token, pesajes() + "?animalId=" + a).andExpect(jsonPath("$.contenido[0].kg").value(412.5));
        // Un pesaje individual de la grilla también cambia la categoría por peso
        api.get(token, url("animales") + "/" + novillito).andExpect(jsonPath("$.categoria").value("NOVILLO"));
    }

    @Test
    void laGrillaNoGuardaNadaSiAlgunaFilaFalla() throws Exception {
        crearAnimal("V-1", "HEMBRA", "VACA");

        api.post(token, pesajes() + "/grilla", grilla(HOY, fila("V-1", 400), fila("NO-EXISTE", 300), fila("V-1", 410)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='pesajes[1].caravana')].message")
                .value("No hay un animal activo con la caravana NO-EXISTE"))
            .andExpect(jsonPath("$.fieldErrors[?(@.field=='pesajes[2].caravana')].message")
                .value("La caravana V-1 está repetida"));

        api.post(token, pesajes() + "/grilla", grilla(HOY, fila("V-1", 0)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("pesajes[0].kg"));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void laGrillaNoAceptaAnimalesQueNoEstanActivosNiFechasFuturas() throws Exception {
        String vendida = crearAnimal("V-1", "HEMBRA", "VACA");
        crearAnimal("V-2", "HEMBRA", "VACA");
        jdbc.update("UPDATE animal SET estado = 'VENDIDO' WHERE id = ?", UUID.fromString(vendida));

        api.post(token, pesajes() + "/grilla", grilla(HOY, fila("V-1", 400)))
            .andExpect(status().isBadRequest());
        api.post(token, pesajes() + "/grilla", grilla(HOY.plusDays(2), fila("V-2", 400)))
            .andExpect(status().isBadRequest());
        api.post(token, pesajes() + "/grilla", Map.of("fecha", HOY.toString(), "pesajes", List.of()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reenviarLaMismaGrillaNoDuplica() throws Exception {
        crearAnimal("V-1", "HEMBRA", "VACA");
        var body = grilla(HOY, fila("V-1", 400));
        body.put("operacionId", UUID.randomUUID().toString());

        api.post(token, pesajes() + "/grilla", body).andExpect(status().isCreated());
        api.post(token, pesajes() + "/grilla", body).andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(1));

        api.get(token, eventos()).andExpect(jsonPath("$.total").value(1));
    }

    // ---------- Ganancia diaria ----------

    @Test
    void calculaLaGananciaDiariaContraElPesajeAnterior() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        api.post(token, eventos(), pesaje(HOY.minusDays(40), List.of(vaca), Map.of("kg", 400))).andExpect(status().isCreated());
        api.post(token, eventos(), pesaje(HOY.minusDays(10), List.of(vaca), Map.of("kg", 430))).andExpect(status().isCreated());
        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("kg", 425.5))).andExpect(status().isCreated());

        api.get(token, pesajes() + "?animalId=" + vaca)
            .andExpect(jsonPath("$.total").value(3))
            // Hoy: 425,5 - 430 en 10 días
            .andExpect(jsonPath("$.contenido[0].kg").value(425.5))
            .andExpect(jsonPath("$.contenido[0].kgAnterior").value(430))
            .andExpect(jsonPath("$.contenido[0].variacionKg").value(-4.5))
            .andExpect(jsonPath("$.contenido[0].diasDesdeAnterior").value(10))
            .andExpect(jsonPath("$.contenido[0].gananciaDiaria").value(-0.45))
            // Hace 10 días: 430 - 400 en 30 días
            .andExpect(jsonPath("$.contenido[1].gananciaDiaria").value(1.0))
            .andExpect(jsonPath("$.contenido[1].diasDesdeAnterior").value(30))
            .andExpect(jsonPath("$.contenido[1].fechaAnterior").value(HOY.minusDays(40).toString()))
            // El primero no tiene con qué compararse
            .andExpect(jsonPath("$.contenido[2].gananciaDiaria").doesNotExist())
            .andExpect(jsonPath("$.contenido[2].kgAnterior").doesNotExist());
    }

    @Test
    void laGananciaIgnoraPesajesAnuladosYUsaElAnteriorAunqueSeCarguePorOrdenInverso() throws Exception {
        String vaca = crearAnimal("V-1", "HEMBRA", "VACA");
        // Se carga primero el más nuevo y después uno viejo (ej: planilla de papel pasada tarde)
        api.post(token, eventos(), pesaje(HOY, List.of(vaca), Map.of("kg", 450))).andExpect(status().isCreated());
        api.post(token, eventos(), pesaje(HOY.minusDays(5), List.of(vaca), Map.of("kg", 999))).andExpect(status().isCreated());
        api.post(token, eventos(), pesaje(HOY.minusDays(20), List.of(vaca), Map.of("kg", 410))).andExpect(status().isCreated());

        String idErrado = objectMapper.readTree(api.get(token, eventos() + "?animalId=" + vaca)
            .andReturn().getResponse().getContentAsString()).get("contenido").get(1).get("id").asText();
        api.delete(token, eventos() + "/" + idErrado).andExpect(status().isNoContent());

        // Con el errado anulado, hoy se compara contra el de hace 20 días: 40 kg en 20 días
        api.get(token, pesajes() + "?animalId=" + vaca)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.contenido[0].kgAnterior").value(410))
            .andExpect(jsonPath("$.contenido[0].gananciaDiaria").value(2.0));
    }

    @Test
    void laGananciaEsPorAnimalAunqueElListadoMezcleVarios() throws Exception {
        String a = crearAnimal("V-1", "HEMBRA", "VACA");
        String b = crearAnimal("V-2", "HEMBRA", "VACA");
        api.post(token, pesajes() + "/grilla", grilla(HOY.minusDays(10), fila("V-1", 400), fila("V-2", 300)))
            .andExpect(status().isCreated());
        api.post(token, pesajes() + "/grilla", grilla(HOY, fila("V-1", 410), fila("V-2", 320)))
            .andExpect(status().isCreated());

        api.get(token, pesajes() + "?desde=" + HOY)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.contenido[?(@.animal.id=='" + a + "')].gananciaDiaria").value(1.0))
            .andExpect(jsonPath("$.contenido[?(@.animal.id=='" + b + "')].gananciaDiaria").value(2.0));
    }

    // ---------- Permisos ----------

    @ParameterizedTest
    @CsvSource({"ENCARGADO, 201", "VETERINARIO, 403", "SOCIO, 403"})
    void pesanQuienesGestionanAnimalesYTodosVenLosPesajes(Rol rol, int esperado) throws Exception {
        crearAnimal("V-1", "HEMBRA", "VACA");
        String tokenRol = tokenConRol(rol);

        api.post(tokenRol, pesajes() + "/grilla", grilla(HOY, fila("V-1", 400))).andExpect(status().is(esperado));
        api.get(tokenRol, pesajes()).andExpect(status().isOk());
    }

    @Test
    void otraOrganizacionNoVeNiCargaPesajes() throws Exception {
        crearAnimal("V-1", "HEMBRA", "VACA");
        String tokenB = api.registrarCuenta("Organización B");

        api.get(tokenB, pesajes()).andExpect(status().isNotFound());
        api.post(tokenB, pesajes() + "/grilla", grilla(HOY, fila("V-1", 400))).andExpect(status().isNotFound());
    }

    // ---------- Helpers ----------

    private String eventos() {
        return url("eventos");
    }

    private String pesajes() {
        return url("pesajes");
    }

    private String url(String recurso) {
        return "/api/establecimientos/" + establecimientoId + "/" + recurso;
    }

    private static Map<String, Object> pesaje(LocalDate fecha, List<String> animalIds, Map<String, Object> datos) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", "PESAJE");
        body.put("fecha", fecha.toString());
        body.put("animalIds", animalIds);
        body.put("datos", datos);
        return body;
    }

    private static Map<String, Object> fila(String caravana, double kg) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("caravana", caravana);
        fila.put("kg", BigDecimal.valueOf(kg));
        return fila;
    }

    @SafeVarargs
    private static Map<String, Object> grilla(LocalDate fecha, Map<String, Object>... filas) {
        Map<String, Object> body = new HashMap<>();
        body.put("fecha", fecha.toString());
        body.put("pesajes", List.of(filas));
        return body;
    }

    private String crearAnimal(String caravana, String sexo, String categoria) throws Exception {
        return crearAnimalEn(caravana, sexo, categoria, null);
    }

    private String crearAnimalEn(String caravana, String sexo, String categoria, String loteId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", sexo);
        body.put("categoria", categoria);
        body.put("loteId", loteId);
        return api.leer(api.post(token, url("animales"), body).andExpect(status().isCreated()), "id");
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
