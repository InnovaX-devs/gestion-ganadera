package com.innovax.ganadera.clima.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/establecimientos/{id}/clima} (#29). Temperaturas en °C, viento en km/h, lluvia
 * en mm, probabilidad y humedad en %. {@code codigo} es el código WMO del estado del tiempo (la web
 * lo traduce a texto e ícono). Cualquier valor puede faltar si el servicio no lo informa.
 *
 * @param lluviaEsperadaMm suma de la lluvia pronosticada para los días de {@code dias}
 * @param consultadoEn cuándo se pidió a Open-Meteo (el pronóstico se reutiliza un rato)
 */
public record ClimaResponse(
    Ubicacion ubicacion,
    Actual actual,
    List<Dia> dias,
    BigDecimal lluviaEsperadaMm,
    String fuente,
    Instant consultadoEn
) {

    /**
     * @param origen COORDENADAS (las del establecimiento) o LOCALIDAD (buscadas por nombre)
     * @param nombre "Río Cuarto, Córdoba" si se buscó por localidad
     */
    public record Ubicacion(double latitud, double longitud, String origen, String nombre) {
    }

    public record Actual(LocalDateTime hora, BigDecimal temperatura, BigDecimal sensacionTermica, Integer humedad,
                         BigDecimal viento, BigDecimal precipitacion, Integer codigo) {
    }

    public record Dia(LocalDate fecha, Integer codigo, BigDecimal maxima, BigDecimal minima, BigDecimal lluviaMm,
                      Integer probabilidadLluvia) {
    }
}
