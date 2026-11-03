package com.innovax.ganadera.cobro;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovax.ganadera.animal.categoria.CategoriaConfig;
import com.innovax.ganadera.support.ApiTestClient;
import com.innovax.ganadera.support.DbCleaner;
import com.innovax.ganadera.support.FakeEmailSender;
import com.innovax.ganadera.support.FakeMercadoPago;

/**
 * Suscripciones con Mercado Pago (#35, segunda parte), contra un Mercado Pago falso. Los tests
 * arrancan con prueba gratis de EMPRESA: acá se mira el plan contratado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuscripcionTest {

    private static final String URL = "/api/suscripcion";
    private static final String SECRETO = "secreto-de-prueba";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of(CategoriaConfig.ZONA_HORARIA));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FakeMercadoPago mp;

    @Autowired
    private FakeEmailSender emails;

    @Autowired
    private SuscripcionService service;

    private ApiTestClient api;
    private String token;
    private String tokenAdmin;
    private String establecimiento;

    @BeforeEach
    void preparar() throws Exception {
        DbCleaner.limpiar(jdbc);
        mp.limpiar();
        emails.limpiar();
        api = new ApiTestClient(mockMvc, objectMapper);
        token = api.leer(api.post(null, "/api/auth/register", Map.of("nombreOrganizacion", "Estancia",
            "nombre", "Juan", "email", "juan@campo.com", "password", ApiTestClient.PASSWORD)), "accessToken");
        tokenAdmin = api.loginAdmin();
        establecimiento = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "Campo de prueba")), "id");
        precio("PRODUCTOR", "15000");
        precio("EMPRESA", "40000");
    }

    @AfterEach
    void limpiar() {
        DbCleaner.limpiar(jdbc);
        mp.limpiar();
    }

    // ---------- Suscribirse y pagar ----------

    @Test
    void suscribirseAutorizarYCobrar() throws Exception {
        api.post(token, URL, Map.of("planCodigo", "productor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value(startsWith("https://mp.test/checkout")));
        String id = mp.ultimoId();
        var creada = mp.ultimaCreada();
        assertEquals(0, new BigDecimal("15000").compareTo(creada.monto()));
        assertEquals("juan@campo.com", creada.emailPagador());
        assertEquals("ARS", creada.moneda());
        assertTrue(creada.descripcion().contains("Productor"));
        // Está en la prueba gratis (hasta HOY + 29): el primer cobro es el día después de que termina
        LocalDate finPrueba = HOY.plusDays(29);
        assertEquals(finPrueba.plusDays(1), creada.primerCobro());

        // Hasta que paga, solo hay un pago pendiente
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.cobro.habilitado").value(true))
            .andExpect(jsonPath("$.cobro.diasGracia").value(7))
            .andExpect(jsonPath("$.cobro.pendiente.planCodigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.pendiente.url").value(startsWith("https://mp.test")))
            .andExpect(jsonPath("$.cobro.pendiente.primerCobro").value(finPrueba.plusDays(1).toString()))
            .andExpect(jsonPath("$.cobro.suscripcion").doesNotExist())
            .andExpect(jsonPath("$.contratado.codigo").value("INICIAL"));

        mp.autorizar(id);
        aviso("subscription_preapproval", id).andExpect(status().isOk());
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.suscripcion.estado").value("ACTIVA"))
            .andExpect(jsonPath("$.cobro.suscripcion.planNombre").value("Productor"))
            .andExpect(jsonPath("$.cobro.suscripcion.primerCobro").value(finPrueba.plusDays(1).toString()))
            // Autorizada sin cobros todavía: cubierta hasta el primer cobro, más la gracia
            .andExpect(jsonPath("$.cobro.suscripcion.enGracia").value(false))
            .andExpect(jsonPath("$.cobro.suscripcion.hasta").value(finPrueba.plusDays(7).toString()))
            .andExpect(jsonPath("$.cobro.pendiente").doesNotExist());

        String cobro = mp.cobrar(id, HOY, true);
        aviso("subscription_authorized_payment", cobro).andExpect(status().isOk());
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.cobro.suscripcion.pagadoHasta").value(HOY.plusMonths(1).toString()))
            .andExpect(jsonPath("$.cobro.suscripcion.enGracia").value(false))
            .andExpect(jsonPath("$.cobro.suscripcion.hasta").value(HOY.plusMonths(1).plusDays(7).toString()));
    }

    @Test
    void sinPruebaElPrimerCobroEsEnseguidaYConPagoManualDespuesDeLoPago() throws Exception {
        sinPrueba();
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR")).andExpect(status().isOk());
        assertEquals(null, mp.ultimaCreada().primerCobro());

        Map<String, Object> pago = new HashMap<>();
        pago.put("planCodigo", "PRODUCTOR");
        pago.put("pagoManualHasta", HOY.plusDays(10).toString());
        api.put(tokenAdmin, "/api/admin/organizaciones/" + organizacion() + "/plan", pago).andExpect(status().isOk());
        api.post(token, URL, Map.of("planCodigo", "EMPRESA")).andExpect(status().isOk());
        assertEquals(HOY.plusDays(11), mp.ultimaCreada().primerCobro());
    }

    @Test
    void alVolverDePagarSeConsultaSinEsperarElAviso() throws Exception {
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"));
        mp.autorizar(mp.ultimoId());
        api.post(token, URL + "/sincronizar", Map.of())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.suscripcion.estado").value("ACTIVA"));
    }

    @Test
    void unLinkSinPagarSeReemplazaPorElNuevo() throws Exception {
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"));
        String viejo = mp.ultimoId();
        api.post(token, URL, Map.of("planCodigo", "EMPRESA")).andExpect(status().isOk());
        String nuevo = mp.ultimoId();

        assertTrue(mp.canceladas().contains(viejo));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.cobro.pendiente.planCodigo").value("EMPRESA"))
            .andExpect(jsonPath("$.cobro.pendiente.url").value(startsWith("https://mp.test/checkout?preapproval_id=" + nuevo)));
    }

    @Test
    void cambiarDePlanCancelaLaSuscripcionAnterior() throws Exception {
        String primera = suscriptoA("PRODUCTOR");
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"))
            .andExpect(status().isUnprocessableEntity());

        String segunda = suscriptoA("EMPRESA");
        assertTrue(mp.canceladas().contains(primera));
        assertEquals("cancelled", mp.estado(primera));
        assertEquals("authorized", mp.estado(segunda));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.contratado.codigo").value("EMPRESA"))
            .andExpect(jsonPath("$.cobro.suscripcion.planCodigo").value("EMPRESA"));
    }

    // ---------- Impago ----------

    @Test
    void siNoSePuedeCobrarHayGraciaDespuesQuedaEnSoloLecturaYVuelveSiPaga() throws Exception {
        sinPrueba();
        String id = suscriptoA("PRODUCTOR");
        LocalDate ultimoCobro = HOY.minusMonths(1).minusDays(2);
        LocalDate pagadoHasta = ultimoCobro.plusMonths(1);
        aviso("subscription_authorized_payment", mp.cobrar(id, ultimoCobro, true));
        aviso("subscription_authorized_payment", mp.cobrar(id, HOY, false));

        // Se pasó de lo pago, pero está en gracia: sigue con su plan y ve el aviso
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.suscripcion.pagadoHasta").value(pagadoHasta.toString()))
            .andExpect(jsonPath("$.cobro.suscripcion.enGracia").value(true))
            .andExpect(jsonPath("$.cobro.suscripcion.hasta").value(pagadoHasta.plusDays(7).toString()));
        assertEquals(0, service.vencer(HOY));

        api.get(token, "/api/establecimientos").andExpect(status().isOk());

        // Terminó la gracia: la cuenta queda en solo lectura (los datos no se tocan)
        assertEquals(1, service.vencer(pagadoHasta.plusDays(8)));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.cobro.habilitada").value(false))
            .andExpect(jsonPath("$.cobro.suscripcion.estado").value("VENCIDA"));
        bloqueada();

        // Mercado Pago reintenta y el cobro sale: vuelve a usarla
        aviso("subscription_authorized_payment", mp.cobrar(id, HOY, true));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.habilitada").value(true))
            .andExpect(jsonPath("$.cobro.suscripcion.estado").value("ACTIVA"));
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")).andExpect(status().isCreated());
    }

    @Test
    void cancelarMantieneElPlanHastaElUltimoDiaPago() throws Exception {
        sinPrueba();
        String id = suscriptoA("PRODUCTOR");
        aviso("subscription_authorized_payment", mp.cobrar(id, HOY, true));
        LocalDate pagadoHasta = HOY.plusMonths(1);

        api.delete(token, URL).andExpect(status().isNoContent());
        assertEquals("cancelled", mp.estado(id));
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.cobro.suscripcion.estado").value("CANCELADA"))
            .andExpect(jsonPath("$.cobro.suscripcion.hasta").value(pagadoHasta.toString()));

        api.get(token, "/api/establecimientos").andExpect(status().isOk());

        // Cancelada no tiene gracia
        assertEquals(0, service.vencer(pagadoHasta));
        assertEquals(1, service.vencer(pagadoHasta.plusDays(1)));
        bloqueada();
        api.delete(token, URL).andExpect(status().isUnprocessableEntity());
    }

    // ---------- Sin uso pago: solo lectura ----------

    @Test
    void alTerminarLaPruebaSinPagarQuedaEnSoloLectura() throws Exception {
        api.get(token, "/api/plan").andExpect(jsonPath("$.cobro.habilitada").value(true));
        sinPrueba();

        api.get(token, "/api/plan")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cobro.habilitada").value(false));
        bloqueada();
        // Lo justo para pagar sigue andando
        api.get(token, "/api/auth/me").andExpect(status().isOk());
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR")).andExpect(status().isOk());
        mp.autorizar(mp.ultimoId());
        api.post(token, URL + "/sincronizar", Map.of())
            .andExpect(jsonPath("$.cobro.habilitada").value(true));
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")).andExpect(status().isCreated());
    }

    @Test
    void elPanelCargaUnPagoManual() throws Exception {
        sinPrueba();
        bloqueada();

        Map<String, Object> pago = new HashMap<>();
        pago.put("planCodigo", "PRODUCTOR");
        pago.put("pagoManualHasta", HOY.plusMonths(1).toString());
        api.put(tokenAdmin, "/api/admin/organizaciones/" + organizacion() + "/plan", pago)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.cobro.habilitada").value(true))
            .andExpect(jsonPath("$.plan.cobro.pagoManualHasta").value(HOY.plusMonths(1).toString()));
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro")).andExpect(status().isCreated());

        // Vencido (o quitado) vuelve a quedar en solo lectura
        pago.put("pagoManualHasta", HOY.minusDays(1).toString());
        api.put(tokenAdmin, "/api/admin/organizaciones/" + organizacion() + "/plan", pago)
            .andExpect(jsonPath("$.plan.cobro.habilitada").value(false));
        bloqueada();
    }

    @Test
    void elPanelRegistraPagosPorTransferencia() throws Exception {
        sinPrueba();
        bloqueada();
        String url = "/api/admin/organizaciones/" + organizacion() + "/registrar-pago";
        LocalDate vence = PagoManual.siguienteVencimiento(null, HOY, 10);
        assertEquals(10, vence.getDayOfMonth());

        api.post(tokenAdmin, url, Map.of())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.cobro.habilitada").value(true))
            .andExpect(jsonPath("$.plan.cobro.pagoManualHasta").value(vence.toString()))
            .andExpect(jsonPath("$.plan.cobro.diaVencimientoManual").value(10));
        // Ya puede cargar (en Inicial: un animal, porque el único establecimiento ya está)
        api.post(token, "/api/establecimientos/" + establecimiento + "/animales",
            Map.of("caravana", "A-1", "sexo", "HEMBRA", "categoria", "VACA")).andExpect(status().isCreated());

        // Paga el mes siguiente por adelantado: suma un mes sin perder días
        api.post(tokenAdmin, url, Map.of())
            .andExpect(jsonPath("$.plan.cobro.pagoManualHasta").value(vence.plusMonths(1).toString()));
        api.post(token, url, Map.of()).andExpect(status().isForbidden());
    }

    // ---------- Avisos ----------

    @Test
    void rechazaAvisosConFirmaInvalida() throws Exception {
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"));
        String id = mp.ultimoId();
        mp.autorizar(id);

        mockMvc.perform(post("/api/mercadopago/webhook").param("type", "subscription_preapproval").param("data.id", id)
                .header("x-signature", "ts=1,v1=" + "0".repeat(64)).header("x-request-id", "x"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/mercadopago/webhook").param("type", "subscription_preapproval").param("data.id", id))
            .andExpect(status().isUnauthorized());
        api.get(token, "/api/plan").andExpect(jsonPath("$.contratado.codigo").value("INICIAL"));

        // Avisos de otras cosas (pagos sueltos, etc.) se aceptan y se ignoran
        aviso("payment", "123").andExpect(status().isOk());
    }

    @Test
    void elIdDelAvisoPuedeVenirEnElCuerpo() throws Exception {
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"));
        String id = mp.ultimoId();
        mp.autorizar(id);
        String ts = "1704908010";
        mockMvc.perform(post("/api/mercadopago/webhook")
                .header("x-signature", "ts=" + ts + ",v1=" + FirmaWebhook.firmar(SECRETO, id, "r-1", ts))
                .header("x-request-id", "r-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"" + id + "\"}}"))
            .andExpect(status().isOk());
        api.get(token, "/api/plan").andExpect(jsonPath("$.contratado.codigo").value("PRODUCTOR"));
    }

    // ---------- Validaciones ----------

    @Test
    void validaciones() throws Exception {
        // Sin precio no se contrata online
        api.post(token, URL, Map.of("planCodigo", "INICIAL")).andExpect(status().isUnprocessableEntity());
        api.post(token, URL, Map.of("planCodigo", "ORO")).andExpect(status().isUnprocessableEntity());
        api.post(token, URL, Map.of("planCodigo", "")).andExpect(status().isBadRequest());
        api.delete(token, URL).andExpect(status().isUnprocessableEntity());
        api.post(null, URL, Map.of("planCodigo", "PRODUCTOR")).andExpect(status().isUnauthorized());

        // Si Mercado Pago no responde, no queda nada a medias
        mp.caer(true);
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value(startsWith("No pudimos comunicarnos con Mercado Pago")));
        mp.caer(false);
        api.get(token, "/api/plan").andExpect(jsonPath("$.cobro.pendiente").doesNotExist());
    }

    @Test
    void soloElDuenoManejaLaSuscripcion() throws Exception {
        String est = api.leer(api.post(token, "/api/establecimientos", Map.of("nombre", "La Esperanza")), "id");
        api.post(token, "/api/establecimientos/" + est + "/invitaciones", Map.of("email", "socio@campo.com", "rol", "SOCIO"))
            .andExpect(status().isCreated());
        String tokenSocio = api.leer(api.post(null, "/api/invitaciones/" + emails.ultimoToken() + "/registro",
            Map.of("nombre", "Socio", "password", ApiTestClient.PASSWORD)), "accessToken");

        api.post(tokenSocio, URL, Map.of("planCodigo", "PRODUCTOR"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(
                "Solo el dueño de la cuenta (juan@campo.com) puede manejar la suscripción."));
        // Ver el estado sí puede
        api.get(tokenSocio, "/api/plan").andExpect(jsonPath("$.cobro.habilitado").value(true));
    }

    // ---------- Precios desde el panel ----------

    @Test
    void elPanelCambiaLosPrecios() throws Exception {
        api.get(token, "/api/plan")
            .andExpect(jsonPath("$.planes[1].codigo").value("PRODUCTOR"))
            .andExpect(jsonPath("$.planes[1].precioMensual", pesos("15000")));
        api.get(tokenAdmin, "/api/admin/planes").andExpect(jsonPath("$[2].precioMensual", pesos("40000")));

        Map<String, Object> sinPrecio = new HashMap<>();
        sinPrecio.put("precioMensual", null);
        api.put(tokenAdmin, "/api/admin/planes/productor", sinPrecio)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.precioMensual").doesNotExist());
        api.post(token, URL, Map.of("planCodigo", "PRODUCTOR")).andExpect(status().isUnprocessableEntity());

        api.put(tokenAdmin, "/api/admin/planes/EMPRESA", Map.of("precioMensual", -1)).andExpect(status().isBadRequest());
        api.put(tokenAdmin, "/api/admin/planes/ORO", Map.of("precioMensual", 10)).andExpect(status().isUnprocessableEntity());
        api.put(token, "/api/admin/planes/EMPRESA", Map.of("precioMensual", 1)).andExpect(status().isForbidden());
    }

    // ---------- Ayudas ----------

    /** La prueba gratis terminó ayer. */
    private void sinPrueba() throws Exception {
        jdbc.update("UPDATE organizacion SET prueba_hasta = ? WHERE id = ?", HOY.minusDays(1),
            UUID.fromString(organizacion()));
    }

    private String organizacion() throws Exception {
        return api.leer(api.get(token, "/api/auth/me"), "organizacionId");
    }

    /**
     * Sin uso pago: solo lectura. Se ve todo, lo que carga o modifica responde 402, y lo cargado sin
     * señal en el campo se sincroniza igual.
     */
    private void bloqueada() throws Exception {
        api.get(token, "/api/establecimientos").andExpect(status().isOk());
        api.get(token, "/api/establecimientos/" + establecimiento + "/animales").andExpect(status().isOk());
        api.get(token, "/api/plan").andExpect(status().isOk());
        api.post(token, "/api/establecimientos", Map.of("nombre", "Otro"))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.codigo").value("PAGO_REQUERIDO"))
            .andExpect(jsonPath("$.message").value(startsWith("Tu cuenta está en solo lectura")));
        api.post(token, "/api/establecimientos/" + establecimiento + "/animales",
            Map.of("caravana", "A-1", "sexo", "HEMBRA", "categoria", "VACA")).andExpect(status().isPaymentRequired());
        api.delete(token, "/api/establecimientos/" + establecimiento).andExpect(status().isPaymentRequired());
        api.post(token, "/api/sync", Map.of("establecimientoId", establecimiento)).andExpect(status().isOk());
    }

    /** Compara como número: la base puede devolver 15000.00 */
    private static Matcher<Object> pesos(String esperado) {
        return new CustomMatcher<>("el número " + esperado) {
            @Override
            public boolean matches(Object valor) {
                return valor instanceof Number n && new BigDecimal(n.toString()).compareTo(new BigDecimal(esperado)) == 0;
            }
        };
    }

    private void precio(String plan, String precio) throws Exception {
        api.put(tokenAdmin, "/api/admin/planes/" + plan, Map.of("precioMensual", new BigDecimal(precio)))
            .andExpect(status().isOk());
    }

    /** Se suscribe, el cliente paga y llega el aviso. Devuelve el id en Mercado Pago. */
    private String suscriptoA(String plan) throws Exception {
        api.post(token, URL, Map.of("planCodigo", plan)).andExpect(status().isOk());
        String id = mp.ultimoId();
        mp.autorizar(id);
        aviso("subscription_preapproval", id).andExpect(status().isOk());
        return id;
    }

    private ResultActions aviso(String tipo, String id) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String requestId = UUID.randomUUID().toString();
        return mockMvc.perform(post("/api/mercadopago/webhook")
            .param("type", tipo)
            .param("data.id", id)
            .header("x-signature", "ts=" + ts + ",v1=" + FirmaWebhook.firmar(SECRETO, id, requestId, ts))
            .header("x-request-id", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"" + tipo + "\",\"data\":{\"id\":\"" + id + "\"}}"));
    }
}
