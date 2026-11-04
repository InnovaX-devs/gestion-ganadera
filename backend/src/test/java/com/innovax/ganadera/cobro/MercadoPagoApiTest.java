package com.innovax.ganadera.cobro;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.innovax.ganadera.cobro.MercadoPago.NuevaSuscripcion;
import com.innovax.ganadera.common.error.BusinessException;

/** Mercado Pago simulado: no sale a internet. */
class MercadoPagoApiTest {

    private static final String URL = "https://mp.test";

    private MockRestServiceServer servidor;
    private MercadoPagoApi api;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        api = new MercadoPagoApi(builder.build(),
            new CobroProperties("APP_USR-x", "", URL, "", "", "ARS", 7, null, true, null, 10));
    }

    private static NuevaSuscripcion suscripcion() {
        return new NuevaSuscripcion("Plan Inicial", "org", "vendedor@innovax.test", new BigDecimal("100"), "ARS",
            "https://web.test/plan", null);
    }

    @Test
    void siPagaElMismoQueCobraLoDiceClaro() {
        servidor.expect(requestTo(URL + "/preapproval"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Payer and collector cannot be the same user\",\"status\":400}"));

        assertThatThrownBy(() -> api.crear(suscripcion()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("otro email");
    }

    @Test
    void otroRechazoSigueSiendoUnErrorDeMercadoPago() {
        servidor.expect(requestTo(URL + "/preapproval"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Invalid back_url\",\"status\":400}"));

        assertThatThrownBy(() -> api.crear(suscripcion())).isInstanceOf(MercadoPagoException.class);
    }
}
