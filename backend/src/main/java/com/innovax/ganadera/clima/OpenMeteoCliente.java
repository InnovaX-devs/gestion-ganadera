package com.innovax.ganadera.clima;

import java.math.BigDecimal;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Open-Meteo (https://open-meteo.com): pronóstico y geocodificación, sin clave. La API gratuita es
 * para uso no comercial (menos de 10.000 llamadas por día) y exige citar la fuente; para uso
 * comercial hay un plan con clave (ver {@link ClimaProperties#apiKey()} y docs/clima.md).
 */
@Component
public class OpenMeteoCliente implements ProveedorClima {

    private static final String ACTUAL =
        "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,precipitation,weather_code";
    private static final String DIARIO =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max";

    private final RestClient http;
    private final ClimaProperties propiedades;

    public OpenMeteoCliente(@Qualifier("climaRestClient") RestClient http, ClimaProperties propiedades) {
        this.http = http;
        this.propiedades = propiedades;
    }

    // ---------- Respuestas de Open-Meteo (solo los campos que se usan) ----------

    public record RespuestaPronostico(ActualOM current, DiarioOM daily) {
    }

    public record ActualOM(
        String time,
        @JsonProperty("temperature_2m") BigDecimal temperatura,
        @JsonProperty("apparent_temperature") BigDecimal sensacion,
        @JsonProperty("relative_humidity_2m") Integer humedad,
        @JsonProperty("wind_speed_10m") BigDecimal viento,
        BigDecimal precipitation,
        @JsonProperty("weather_code") Integer codigo
    ) {
    }

    public record DiarioOM(
        List<String> time,
        @JsonProperty("weather_code") List<Integer> codigo,
        @JsonProperty("temperature_2m_max") List<BigDecimal> maxima,
        @JsonProperty("temperature_2m_min") List<BigDecimal> minima,
        @JsonProperty("precipitation_sum") List<BigDecimal> lluvia,
        @JsonProperty("precipitation_probability_max") List<Integer> probabilidad
    ) {
    }

    public record RespuestaGeocoding(List<ResultadoGeocoding> results) {
    }

    public record ResultadoGeocoding(String name, double latitude, double longitude, String admin1) {
    }

    // ---------- Consultas ----------

    @Override
    public Pronostico pronostico(double latitud, double longitud, int dias) {
        URI uri = conClave(UriComponentsBuilder.fromUriString(propiedades.url() + "/forecast")
            .queryParam("latitude", latitud)
            .queryParam("longitude", longitud)
            .queryParam("current", ACTUAL)
            .queryParam("daily", DIARIO)
            .queryParam("forecast_days", dias)
            .queryParam("wind_speed_unit", "kmh")
            // Las fechas y horas en la hora local del establecimiento
            .queryParam("timezone", "auto"));
        RespuestaPronostico r = http.get().uri(uri).retrieve().body(RespuestaPronostico.class);
        return convertir(r);
    }

    @Override
    public Optional<Lugar> buscarLocalidad(String localidad, String provincia) {
        URI uri = conClave(UriComponentsBuilder.fromUriString(propiedades.geocodingUrl() + "/search")
            .queryParam("name", localidad)
            .queryParam("count", 10)
            .queryParam("language", "es")
            .queryParam("countryCode", "AR"));
        RespuestaGeocoding r = http.get().uri(uri).retrieve().body(RespuestaGeocoding.class);
        return elegir(r, provincia);
    }

    private URI conClave(UriComponentsBuilder uri) {
        if (propiedades.apiKey() != null && !propiedades.apiKey().isBlank()) {
            uri.queryParam("apikey", propiedades.apiKey());
        }
        return uri.build().encode().toUri();
    }

    static Pronostico convertir(RespuestaPronostico r) {
        if (r == null || r.current() == null || r.daily() == null || r.daily().time() == null) {
            throw new IllegalStateException("Open-Meteo devolvió una respuesta incompleta");
        }
        ActualOM a = r.current();
        Actual actual = new Actual(hora(a.time()), a.temperatura(), a.sensacion(), a.humedad(), a.viento(),
            a.precipitation(), a.codigo());

        DiarioOM d = r.daily();
        List<Dia> dias = new ArrayList<>();
        for (int i = 0; i < d.time().size(); i++) {
            dias.add(new Dia(LocalDate.parse(d.time().get(i)), elemento(d.codigo(), i), elemento(d.maxima(), i),
                elemento(d.minima(), i), elemento(d.lluvia(), i), elemento(d.probabilidad(), i)));
        }
        return new Pronostico(actual, dias);
    }

    /**
     * Entre los resultados, el de la provincia del establecimiento (sin importar tildes ni
     * mayúsculas). Sin provincia o si ninguno coincide, el primero (el más poblado).
     */
    static Optional<Lugar> elegir(RespuestaGeocoding r, String provincia) {
        if (r == null || r.results() == null || r.results().isEmpty()) return Optional.empty();
        ResultadoGeocoding elegido = r.results().stream()
            .filter(x -> provincia != null && !provincia.isBlank() && normalizar(x.admin1()).equals(normalizar(provincia)))
            .findFirst()
            .orElse(r.results().get(0));
        String nombre = elegido.admin1() == null ? elegido.name() : elegido.name() + ", " + elegido.admin1();
        return Optional.of(new Lugar(elegido.latitude(), elegido.longitude(), nombre));
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase().replace("provincia de ", "").trim();
    }

    private static <T> T elemento(List<T> lista, int i) {
        return lista != null && i < lista.size() ? lista.get(i) : null;
    }

    /** "2026-10-30T14:15" (hora local del lugar). */
    private static LocalDateTime hora(String texto) {
        try {
            return texto == null ? null : LocalDateTime.parse(texto);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
