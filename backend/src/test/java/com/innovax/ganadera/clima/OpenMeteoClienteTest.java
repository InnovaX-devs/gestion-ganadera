package com.innovax.ganadera.clima;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Open-Meteo simulado: no sale a internet. */
class OpenMeteoClienteTest {

    private static final String RESPUESTA = """
        {
          "latitude": -33.12, "longitude": -64.35, "timezone": "America/Argentina/Cordoba",
          "current": {
            "time": "2026-10-30T14:15", "interval": 900, "temperature_2m": 24.3,
            "apparent_temperature": 23.1, "relative_humidity_2m": 41, "wind_speed_10m": 18.7,
            "precipitation": 0.0, "weather_code": 2
          },
          "daily": {
            "time": ["2026-10-30", "2026-10-31", "2026-11-01"],
            "weather_code": [2, 61, 95],
            "temperature_2m_max": [27.1, 22.4, 25.0],
            "temperature_2m_min": [12.3, 14.0, 15.2],
            "precipitation_sum": [0.0, 8.4, 21.0],
            "precipitation_probability_max": [5, 70, 85]
          }
        }
        """;

    private static OpenMeteoCliente cliente(RestClient.Builder builder, String apiKey) {
        return new OpenMeteoCliente(builder.build(), new ClimaProperties(
            "https://clima.test/v1", "https://geo.test/v1", apiKey, 3, Duration.ofMinutes(30), Duration.ofSeconds(1)));
    }

    @Test
    void pideElPronosticoYLoConvierte() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(startsWith("https://clima.test/v1/forecast")))
            .andExpect(queryParam("latitude", "-33.12"))
            .andExpect(queryParam("forecast_days", "3"))
            .andExpect(queryParam("timezone", "auto"))
            .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        var p = cliente(builder, "").pronostico(-33.12, -64.35, 3);

        assertThat(p.actual().hora()).isEqualTo(LocalDateTime.of(2026, 10, 30, 14, 15));
        assertThat(p.actual().temperatura()).isEqualByComparingTo("24.3");
        assertThat(p.actual().humedad()).isEqualTo(41);
        assertThat(p.actual().codigo()).isEqualTo(2);
        assertThat(p.dias()).hasSize(3);
        assertThat(p.dias().get(1).fecha()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(p.dias().get(1).lluviaMm()).isEqualByComparingTo("8.4");
        assertThat(p.dias().get(2).probabilidadLluvia()).isEqualTo(85);
        servidor.verify();
    }

    @Test
    void conClaveLaMandaComoParametro() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(startsWith("https://clima.test/v1/forecast")))
            .andExpect(queryParam("apikey", "secreta"))
            .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        cliente(builder, "secreta").pronostico(-33.12, -64.35, 3);

        servidor.verify();
    }

    @Test
    void buscaLaLocalidadEnArgentinaYPrefiereLaDeSuProvincia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(startsWith("https://geo.test/v1/search")))
            .andExpect(queryParam("countryCode", "AR"))
            .andRespond(withSuccess("""
                {"results": [
                  {"name": "San Martín", "latitude": -34.57, "longitude": -58.53, "admin1": "Buenos Aires"},
                  {"name": "San Martín", "latitude": -33.08, "longitude": -68.47, "admin1": "Mendoza"}
                ]}
                """, MediaType.APPLICATION_JSON));

        var lugar = cliente(builder, "").buscarLocalidad("San Martín", "MENDOZA");

        assertThat(lugar).isPresent();
        assertThat(lugar.get().latitud()).isEqualTo(-33.08);
        assertThat(lugar.get().nombre()).isEqualTo("San Martín, Mendoza");
    }

    @Test
    void eligeSinImportarTildesYSiNingunaCoincideUsaLaPrimera() {
        var r = new OpenMeteoCliente.RespuestaGeocoding(List.of(
            new OpenMeteoCliente.ResultadoGeocoding("Río Cuarto", -33.12, -64.35, "Córdoba")));

        assertThat(OpenMeteoCliente.elegir(r, "cordoba")).get()
            .extracting(ProveedorClima.Lugar::nombre).isEqualTo("Río Cuarto, Córdoba");
        assertThat(OpenMeteoCliente.elegir(r, "Santa Fe")).get()
            .extracting(ProveedorClima.Lugar::latitud).isEqualTo(-33.12);
        assertThat(OpenMeteoCliente.elegir(new OpenMeteoCliente.RespuestaGeocoding(null), "Córdoba")).isEmpty();
    }
}
