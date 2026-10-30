package com.innovax.ganadera.mercado.fuente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.innovax.ganadera.mercado.Producto;

/** Lectura de la página de precios pizarra (sin salir a internet). */
class PizarraRosarioFuenteTest {

    /** Armada como la página real: menú, título con la fecha y un bloque por grano. */
    private static String pagina(String soja) {
        return """
            <html><head><title>Precios de Pizarra</title><script>var x = "Trigo $1,00";</script></head>
            <body>
              <nav><a href="/es/precios-de-pizarra">Precios de Pizarra</a> <a href="#">Trigo y Ma&iacute;z</a></nav>
              <h3>Precios Pizarra del d&iacute;a 29/10/2026</h3>
              <div class="grano"><h3>Trigo</h3><p class="precio">$311.000,00</p><p><b>US$</b> 203,27</p></div>
              <div class="grano"><h3>Ma&iacute;z</h3><p class="precio">$275.400,00</p><p><b>US$</b> 180,00</p></div>
              <div class="grano"><h3>Girasol</h3><p>S/C (E) $810.900,00</p></div>
              %s
              <p>Precios corrientes expresados en $/Tn. TC BNA Divisas Comprador 29/10/2026: $ 1.530,00</p>
            </body></html>
            """.formatted(soja);
    }

    private static Map<Producto, Lectura> leer(String html) {
        List<Lectura> lecturas = PizarraRosarioFuente.leer(html);
        return lecturas.stream().collect(Collectors.toMap(Lectura::producto, Function.identity()));
    }

    @Test
    void leeLosTresGranosConLaFechaDeLaPizarra() {
        var lecturas = leer(pagina("<div class=\"grano\"><h3>Soja</h3><p class=\"precio\">$547.000,00</p></div>"));

        assertThat(lecturas.get(Producto.TRIGO).valor()).isEqualByComparingTo(new BigDecimal("311000.00"));
        assertThat(lecturas.get(Producto.MAIZ).valor()).isEqualByComparingTo(new BigDecimal("275400"));
        assertThat(lecturas.get(Producto.SOJA).valor()).isEqualByComparingTo(new BigDecimal("547000"));
        assertThat(lecturas.values()).allSatisfy(l -> {
            assertThat(l.esOk()).isTrue();
            assertThat(l.fechaDato()).isEqualTo(LocalDate.of(2026, 10, 29));
            assertThat(l.nota()).isNull();
        });
    }

    @Test
    void elPrecioEstimativoSeGuardaConUnaNota() {
        var lecturas = leer(pagina("<div class=\"grano\"><h3>Soja</h3><p>S/C (E) $547.000,00</p></div>"));

        assertThat(lecturas.get(Producto.SOJA).esOk()).isTrue();
        assertThat(lecturas.get(Producto.SOJA).valor()).isEqualByComparingTo("547000");
        assertThat(lecturas.get(Producto.SOJA).nota()).isEqualTo(PizarraRosarioFuente.NOTA_ESTIMATIVO);
        assertThat(lecturas.get(Producto.TRIGO).nota()).isNull();
    }

    @Test
    void sinPrecioParaUnGranoEseFallaYLosDemasNo() {
        var lecturas = leer(pagina("<div class=\"grano\"><h3>Soja</h3><p>S/C</p></div>"));

        assertThat(lecturas.get(Producto.SOJA).esOk()).isFalse();
        assertThat(lecturas.get(Producto.SOJA).error()).contains("soja");
        assertThat(lecturas.get(Producto.TRIGO).esOk()).isTrue();
        assertThat(lecturas.get(Producto.MAIZ).esOk()).isTrue();
    }

    @Test
    void siLaPaginaCambioDeFormatoFallaConUnMensajeClaro() {
        assertThatThrownBy(() -> PizarraRosarioFuente.leer("<html><body>Mantenimiento</body></html>"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Precios Pizarra del día");
    }

    @Test
    void textoHtmlSacaEtiquetasScriptsYEntidades() {
        assertThat(TextoHtml.aTexto("<p>Ma&iacute;z&nbsp;<b>$</b>1.000,00</p><script>no</script>&#36;"))
            .isEqualTo("Maíz $ 1.000,00 $");
    }
}
