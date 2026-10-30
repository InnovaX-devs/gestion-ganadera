package com.innovax.ganadera.evento.tipos;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/** Issue #23: eventos Venta y Baja (salen del stock) y su anulación. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VentaBajaTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiTestClient api;
    private String token;
    private String campo;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.registrarCuenta("Organización A");
        campo = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza"))
            .andExpect(status().isCreated()), "id");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
    }

    // ---------- Venta ----------

    @Test
    void ventaPorKgCalculaElImporteYSacaAlAnimalDelStock() throws Exception {
        String vaca = crearAnimal("V-1", null);
        crearAnimal("V-2", null);

        api.post(token, eventos(), venta(List.of(vaca), datosVenta("POR_KG", 2500, Map.of("kg", 400.5, "documento", "DTE-123"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].tipo").value("VENTA"))
            .andExpect(jsonPath("$.eventos[0].datos.comprador").value("Frigorífico Sur"))
            .andExpect(jsonPath("$.eventos[0].datos.documento").value("DTE-123"))
            .andExpect(jsonPath("$.eventos[0].datos.kg").value(400.5))
            .andExpect(jsonPath("$.eventos[0].datos.importe").value(1001250.0));

        api.get(token, animales() + "/" + vaca).andExpect(jsonPath("$.estado").value("VENDIDO"));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(1));
        api.get(token, animales()).andExpect(jsonPath("$.total").value(1));
        api.get(token, eventos() + "?animalId=" + vaca).andExpect(jsonPath("$.contenido[0].tipo").value("VENTA"));
    }

    @Test
    void ventaPorCabezaNoNecesitaPeso() throws Exception {
        String vaca = crearAnimal("V-1", null);

        api.post(token, eventos(), venta(List.of(vaca), datosVenta("POR_CABEZA", 900000, Map.of())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].datos.importe").value(900000.0))
            .andExpect(jsonPath("$.eventos[0].datos.kg").doesNotExist());
    }

    @Test
    void ventaMasivaPorLoteRepartePesoTotalEnPromedio() throws Exception {
        String lote = crearAgrupacion("lotes", "Engorde");
        crearAnimal("V-1", lote);
        crearAnimal("V-2", lote);
        crearAnimal("V-3", lote);
        crearAnimal("V-4", lote);
        crearAnimal("V-5", null);

        var body = venta(null, datosVenta("POR_KG", 2000, Map.of("pesoTotal", 1600)));
        body.put("loteId", lote);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(4))
            .andExpect(jsonPath("$.eventos[*].tipo", everyItem(is("VENTA"))))
            .andExpect(jsonPath("$.eventos[*].datos.kg", everyItem(is(400.0))))
            .andExpect(jsonPath("$.eventos[*].datos.cantidad", everyItem(is(4))))
            .andExpect(jsonPath("$.eventos[*].datos.importe", everyItem(is(800000.0))));

        api.get(token, stock()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void ventaMasivaDeVariosAnimalesElegidos() throws Exception {
        String a1 = crearAnimal("V-1", null);
        String a2 = crearAnimal("V-2", null);

        api.post(token, eventos(), venta(List.of(a1, a2), datosVenta("POR_CABEZA", 500000, Map.of())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void validaLosDatosDeLaVenta() throws Exception {
        String a1 = crearAnimal("V-1", null);
        String a2 = crearAnimal("V-2", null);

        // Por kg hace falta el peso
        api.post(token, eventos(), venta(List.of(a1), datosVenta("POR_KG", 2500, Map.of())))
            .andExpect(status().isUnprocessableEntity());
        // Varios animales: el peso va como total, no como kg de cada uno
        api.post(token, eventos(), venta(List.of(a1, a2), datosVenta("POR_KG", 2500, Map.of("kg", 400))))
            .andExpect(status().isUnprocessableEntity());
        // Comprador y precio son obligatorios
        var sinComprador = datosVenta("POR_CABEZA", 100, Map.of());
        sinComprador.put("comprador", " ");
        api.post(token, eventos(), venta(List.of(a1), sinComprador))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.comprador"));
        var sinPrecio = datosVenta("POR_CABEZA", 100, Map.of());
        sinPrecio.remove("precio");
        api.post(token, eventos(), venta(List.of(a1), sinPrecio)).andExpect(status().isBadRequest());

        // Nada se vendió
        api.get(token, stock()).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void todoONadaSiAlgunAnimalNoSePuedeVender() throws Exception {
        String a1 = crearAnimal("V-1", null);
        String a2 = crearAnimal("V-2", null);
        api.post(token, eventos(), venta(List.of(a2), datosVenta("POR_CABEZA", 100, Map.of()))).andExpect(status().isCreated());

        // a2 ya está vendido: no se puede vender de nuevo, y a1 tampoco se vende
        api.post(token, eventos(), venta(List.of(a1, a2), datosVenta("POR_CABEZA", 100, Map.of())))
            .andExpect(status().isUnprocessableEntity());
        api.get(token, animales() + "/" + a1).andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    // ---------- Carencia ----------

    @Test
    void unAnimalEnCarenciaNoSeVendeSinConfirmar() throws Exception {
        String vaca = crearAnimalEnCarencia("V-1");
        String libre = crearAnimal("V-2", null);

        api.post(token, eventos(), venta(List.of(vaca, libre), datosVenta("POR_CABEZA", 100, Map.of())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("V-1")));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(2));

        api.post(token, eventos(), venta(List.of(vaca, libre), datosVenta("POR_CABEZA", 100, Map.of("confirmarCarencia", true))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2))
            .andExpect(jsonPath("$.eventos[0].datos.confirmarCarencia").value(true));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void laBajaPorMuerteNoPideConfirmarCarencia() throws Exception {
        String vaca = crearAnimalEnCarencia("V-1");

        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "MUERTE", "causa", "Meteorismo")))
            .andExpect(status().isCreated());
    }

    // ---------- Baja ----------

    @Test
    void bajaPorMuerteExigeCausaYDejaAlAnimalMuerto() throws Exception {
        String vaca = crearAnimal("V-1", null);

        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "MUERTE")))
            .andExpect(status().isUnprocessableEntity());
        api.get(token, animales() + "/" + vaca).andExpect(jsonPath("$.estado").value("ACTIVO"));

        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "MUERTE", "causa", "Mordedura de víbora")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventos[0].tipo").value("BAJA"))
            .andExpect(jsonPath("$.eventos[0].datos.causa").value("Mordedura de víbora"));

        api.get(token, animales() + "/" + vaca).andExpect(jsonPath("$.estado").value("MUERTO"));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void bajaPorPerdidaDejaAlAnimalPerdidoYLaCausaEsOpcional() throws Exception {
        String vaca = crearAnimal("V-1", null);

        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "PERDIDA")))
            .andExpect(status().isCreated());

        api.get(token, animales() + "/" + vaca).andExpect(jsonPath("$.estado").value("PERDIDO"));
        api.get(token, animales() + "?estado=PERDIDO").andExpect(jsonPath("$.total").value(1));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void sePuedeDarDeBajaUnLoteEntero() throws Exception {
        String lote = crearAgrupacion("lotes", "Recría");
        crearAnimal("V-1", lote);
        crearAnimal("V-2", lote);

        var body = baja(null, Map.of("motivo", "MUERTE", "causa", "Tormenta"));
        body.put("loteId", lote);
        api.post(token, eventos(), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cantidad").value(2));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void bajaSinMotivoEsInvalida() throws Exception {
        String vaca = crearAnimal("V-1", null);

        api.post(token, eventos(), baja(List.of(vaca), Map.of("causa", "Algo")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("datos.motivo"));
    }

    // ---------- Anular ----------

    @Test
    void anularUnaBajaDevuelveElAnimalAlStockConSuLote() throws Exception {
        String lote = crearAgrupacion("lotes", "Recría");
        String vaca = crearAnimal("V-1", lote);
        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "MUERTE", "causa", "Error de carga")))
            .andExpect(status().isCreated());
        api.get(token, stock()).andExpect(jsonPath("$.total").value(0));

        api.delete(token, eventos() + "/" + idDelEvento(vaca, "BAJA")).andExpect(status().isNoContent());

        api.get(token, animales() + "/" + vaca)
            .andExpect(jsonPath("$.estado").value("ACTIVO"))
            .andExpect(jsonPath("$.lote.nombre").value("Recría"));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(1));
        // La baja anulada sale del historial
        api.get(token, eventos() + "?animalId=" + vaca).andExpect(jsonPath("$.total").value(0));
        // Y el animal se puede volver a dar de baja
        api.post(token, eventos(), baja(List.of(vaca), Map.of("motivo", "PERDIDA"))).andExpect(status().isCreated());
    }

    @Test
    void anularUnaVentaDevuelveElAnimalAlStock() throws Exception {
        String vaca = crearAnimal("V-1", null);
        api.post(token, eventos(), venta(List.of(vaca), datosVenta("POR_CABEZA", 100, Map.of()))).andExpect(status().isCreated());

        api.delete(token, eventos() + "/" + idDelEvento(vaca, "VENTA")).andExpect(status().isNoContent());

        api.get(token, animales() + "/" + vaca).andExpect(jsonPath("$.estado").value("ACTIVO"));
        api.get(token, stock()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void noSePuedeAnularLaBajaSiOtroAnimalTomoLaCaravana() throws Exception {
        String vieja = crearAnimal("V-1", null);
        api.post(token, eventos(), baja(List.of(vieja), Map.of("motivo", "PERDIDA"))).andExpect(status().isCreated());
        // La caravana quedó libre y se la pone a otro animal
        crearAnimal("V-1", null);

        api.delete(token, eventos() + "/" + idDelEvento(vieja, "BAJA")).andExpect(status().isConflict());
        api.get(token, animales() + "/" + vieja).andExpect(jsonPath("$.estado").value("PERDIDO"));
    }

    // ---------- Auxiliares ----------

    private static String eventos(String establecimiento) {
        return "/api/establecimientos/" + establecimiento + "/eventos";
    }

    private String eventos() {
        return eventos(campo);
    }

    private String animales() {
        return "/api/establecimientos/" + campo + "/animales";
    }

    private String stock() {
        return "/api/stock?establecimientoId=" + campo;
    }

    private static Map<String, Object> datosVenta(String modalidad, Number precio, Map<String, Object> extra) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("comprador", "Frigorífico Sur");
        datos.put("modalidad", modalidad);
        datos.put("precio", precio);
        datos.putAll(extra);
        return datos;
    }

    private static Map<String, Object> venta(List<String> animalIds, Map<String, Object> datos) {
        return evento("VENTA", animalIds, datos);
    }

    private static Map<String, Object> baja(List<String> animalIds, Map<String, Object> datos) {
        return evento("BAJA", animalIds, datos);
    }

    private static Map<String, Object> evento(String tipo, List<String> animalIds, Map<String, Object> datos) {
        Map<String, Object> body = new HashMap<>();
        body.put("tipo", tipo);
        body.put("fecha", HOY.toString());
        body.put("animalIds", animalIds);
        body.put("datos", datos);
        return body;
    }

    private String crearAgrupacion(String recurso, String nombre) throws Exception {
        return api.leer(api.post(token, "/api/establecimientos/" + campo + "/" + recurso, Map.of("nombre", nombre))
            .andExpect(status().isCreated()), "id");
    }

    private String crearAnimal(String caravana, String loteId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("caravana", caravana);
        body.put("sexo", "HEMBRA");
        body.put("categoria", "VACA");
        body.put("loteId", loteId);
        return api.leer(api.post(token, animales(), body).andExpect(status().isCreated()), "id");
    }

    /** Un animal tratado hoy con un producto de 28 días de carencia. */
    private String crearAnimalEnCarencia(String caravana) throws Exception {
        String vaca = crearAnimal(caravana, null);
        String producto = api.leer(api.post(token, "/api/establecimientos/" + campo + "/productos-sanitarios",
            Map.of("nombre", "Ivomec", "tipo", "ANTIPARASITARIO", "diasCarencia", 28))
            .andExpect(status().isCreated()), "id");
        var tratamiento = new HashMap<String, Object>();
        tratamiento.put("tipo", "TRATAMIENTO");
        tratamiento.put("fecha", HOY.toString());
        tratamiento.put("animalIds", List.of(vaca));
        tratamiento.put("datos", Map.of("productoId", producto, "dosis", "5 ml", "via", "SUBCUTANEA"));
        api.post(token, eventos(), tratamiento).andExpect(status().isCreated());
        return vaca;
    }

    /** Id del evento (no anulado) del tipo indicado que tiene el animal. */
    private String idDelEvento(String animalId, String tipo) throws Exception {
        var json = objectMapper.readTree(api.get(token, eventos() + "?tipo=" + tipo + "&animalId=" + animalId)
            .andReturn().getResponse().getContentAsString());
        return json.get("contenido").get(0).get("id").asText();
    }
}
