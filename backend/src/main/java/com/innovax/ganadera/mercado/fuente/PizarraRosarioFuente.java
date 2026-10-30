package com.innovax.ganadera.mercado.fuente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.innovax.ganadera.mercado.MercadoProperties;
import com.innovax.ganadera.mercado.Producto;

/**
 * Maíz, soja y trigo: precios pizarra de la Cámara Arbitral de Cereales de la Bolsa de Comercio de
 * Rosario (https://www.cac.bcr.com.ar/es/precios-de-pizarra), en $/tn. La pizarra que se publica a
 * la mañana es la del día hábil anterior: eso queda en fecha_dato.
 *
 * <p>No hay API pública: se lee la página. Para no depender de cómo está armado el HTML, se pasa a
 * texto y se buscan los valores ("Trigo $311.000,00"). Cuando la Cámara no tiene operaciones publica
 * un estimativo ("S/C (E)"): se guarda igual, con una nota. Ver términos de uso en docs/mercado.md.
 */
@Component
@ConditionalOnProperty(name = "app.mercado.fuentes-externas", havingValue = "true", matchIfMissing = true)
public class PizarraRosarioFuente implements FuenteCotizacion {

    static final String NOTA_ESTIMATIVO = "Estimativo: la Cámara no registró operaciones";

    /** Cómo aparece cada grano en la página. */
    private static final Map<Producto, String> NOMBRES = new LinkedHashMap<>();
    static {
        NOMBRES.put(Producto.TRIGO, "Trigo");
        NOMBRES.put(Producto.MAIZ, "Ma[ií]z");
        NOMBRES.put(Producto.SOJA, "Soja");
    }

    private static final Pattern FECHA = Pattern.compile(
        "Precios\\s+Pizarra\\s+del\\s+d[ií]a\\s+(\\d{1,2}/\\d{1,2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("d/M/yyyy");

    private final RestClient http;
    private final String url;

    public PizarraRosarioFuente(@Qualifier("mercadoRestClient") RestClient http, MercadoProperties propiedades) {
        this.http = http;
        this.url = propiedades.pizarraRosarioUrl();
    }

    @Override
    public String nombre() {
        return "BCR Pizarra Rosario";
    }

    @Override
    public Set<Producto> productos() {
        return NOMBRES.keySet();
    }

    @Override
    public List<Lectura> leer() {
        String html = http.get().uri(url).retrieve().body(String.class);
        return leer(html);
    }

    /** Separado de la descarga para poder probarlo con una página guardada. */
    static List<Lectura> leer(String html) {
        String texto = TextoHtml.aTexto(html == null ? "" : html);
        Matcher fecha = FECHA.matcher(texto);
        if (!fecha.find()) {
            throw new IllegalStateException(
                "No se encontró \"Precios Pizarra del día\" en la página: puede haber cambiado el formato");
        }
        LocalDate fechaDato = fecha(fecha.group(1));
        // Los precios están después del título: así no se confunden con textos del menú
        String precios = texto.substring(fecha.end());

        List<Lectura> lecturas = new ArrayList<>();
        NOMBRES.forEach((producto, nombre) -> lecturas.add(leerProducto(precios, producto, nombre, fechaDato)));
        return lecturas;
    }

    private static Lectura leerProducto(String texto, Producto producto, String nombre, LocalDate fechaDato) {
        // "Soja S/C (E) $547.000,00" o "Trigo $311.000,00"
        Matcher m = Pattern.compile(
            "\\b" + nombre + "\\s+(S/C\\s*\\(E\\)\\s*)?\\$\\s*([\\d.]+(?:,\\d+)?)",
            Pattern.CASE_INSENSITIVE).matcher(texto);
        if (!m.find()) {
            return Lectura.fallo(producto, "La pizarra no informa precio de " + producto.name().toLowerCase());
        }
        BigDecimal valor = numero(m.group(2));
        if (valor.signum() <= 0) {
            return Lectura.fallo(producto, "La pizarra informa un precio inválido para " + producto.name().toLowerCase());
        }
        return Lectura.ok(producto, valor, fechaDato, m.group(1) != null ? NOTA_ESTIMATIVO : null);
    }

    /** "311.000,00" -> 311000.00 */
    private static BigDecimal numero(String texto) {
        return new BigDecimal(texto.replace(".", "").replace(',', '.'));
    }

    private static LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(texto, DD_MM_YYYY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
