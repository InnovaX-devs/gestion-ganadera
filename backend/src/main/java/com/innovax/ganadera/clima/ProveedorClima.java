package com.innovax.ganadera.clima;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * De dónde sale el clima (#29). La implementación es {@link OpenMeteoCliente}; los tests usan una
 * falsa para no salir a internet.
 */
public interface ProveedorClima {

    /** @throws RuntimeException si el servicio no responde o responde algo inválido */
    Pronostico pronostico(double latitud, double longitud, int dias);

    /** Coordenadas de una localidad argentina. Vacío si no se encuentra. */
    Optional<Lugar> buscarLocalidad(String localidad, String provincia);

    record Lugar(double latitud, double longitud, String nombre) {
    }

    /** Valores en °C, km/h, mm y %. Cualquiera puede faltar (null) si el servicio no lo informa. */
    record Pronostico(Actual actual, List<Dia> dias) {
    }

    record Actual(LocalDateTime hora, BigDecimal temperatura, BigDecimal sensacionTermica, Integer humedad,
                  BigDecimal viento, BigDecimal precipitacion, Integer codigo) {
    }

    record Dia(LocalDate fecha, Integer codigo, BigDecimal maxima, BigDecimal minima, BigDecimal lluviaMm,
               Integer probabilidadLluvia) {
    }
}
