package com.innovax.ganadera.mercado.fuente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.innovax.ganadera.mercado.MercadoProperties;
import com.innovax.ganadera.mercado.Producto;

/** DolarApi simulado: no sale a internet. */
class DolarApiFuenteTest {

    private static final String URL = "https://dolarapi.test/v1";

    private MockRestServiceServer servidor;
    private DolarApiFuente fuente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        // Los dos dólares se piden en orden indefinido
        servidor = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        fuente = new DolarApiFuente(builder.build(),
            new MercadoProperties(true, URL, "https://pizarra.test", Duration.ofSeconds(1)));
    }

    private static String json(String venta, String fecha) {
        return """
            {"moneda":"USD","casa":"x","nombre":"X","compra":1400,"venta":%s,"fechaActualizacion":"%s"}
            """.formatted(venta, fecha);
    }

    private Map<Producto, Lectura> leer() {
        return fuente.leer().stream().collect(Collectors.toMap(Lectura::producto, Function.identity()));
    }

    @Test
    void guardaElValorDeVentaConLaFechaEnHoraArgentina() {
        // 23:30 UTC del 29 son las 20:30 del 29 en Argentina
        servidor.expect(requestTo(URL + "/dolares/oficial"))
            .andRespond(withSuccess(json("1455.5", "2026-10-29T23:30:00.000Z"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/dolares/blue"))
            .andRespond(withSuccess(json("1480", "2026-10-30T02:00:00.000Z"), MediaType.APPLICATION_JSON));

        var lecturas = leer();

        assertThat(lecturas.get(Producto.DOLAR_OFICIAL).valor()).isEqualByComparingTo(new BigDecimal("1455.5"));
        assertThat(lecturas.get(Producto.DOLAR_OFICIAL).fechaDato()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(lecturas.get(Producto.DOLAR_BLUE).valor()).isEqualByComparingTo("1480");
        assertThat(lecturas.get(Producto.DOLAR_BLUE).fechaDato()).isEqualTo(LocalDate.of(2026, 10, 29));
        servidor.verify();
    }

    @Test
    void siFallaUnDolarElOtroSeLeeIgual() {
        servidor.expect(requestTo(URL + "/dolares/oficial"))
            .andRespond(withSuccess(json("1455", "2026-10-29T18:00:00Z"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/dolares/blue")).andRespond(withServerError());

        var lecturas = leer();

        assertThat(lecturas.get(Producto.DOLAR_OFICIAL).esOk()).isTrue();
        assertThat(lecturas.get(Producto.DOLAR_BLUE).esOk()).isFalse();
        assertThat(lecturas.get(Producto.DOLAR_BLUE).error()).isNotBlank();
    }

    @Test
    void unaRespuestaSinVentaEsUnFallo() {
        servidor.expect(requestTo(URL + "/dolares/oficial"))
            .andRespond(withSuccess(json("null", "2026-10-29T18:00:00Z"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/dolares/blue"))
            .andRespond(withSuccess(json("1480", "no-es-fecha"), MediaType.APPLICATION_JSON));

        var lecturas = leer();

        assertThat(lecturas.get(Producto.DOLAR_OFICIAL).esOk()).isFalse();
        // Una fecha ilegible no invalida el valor
        assertThat(lecturas.get(Producto.DOLAR_BLUE).esOk()).isTrue();
        assertThat(lecturas.get(Producto.DOLAR_BLUE).fechaDato()).isNull();
    }
}
