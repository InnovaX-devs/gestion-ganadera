package com.innovax.ganadera.clima;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClimaProperties.class)
public class ClimaConfig {

    /** Con timeout: si Open-Meteo no responde, el inicio no se queda esperando. */
    @Bean
    RestClient climaRestClient(RestClient.Builder builder, ClimaProperties propiedades) {
        Duration timeout = propiedades.timeout() != null ? propiedades.timeout() : Duration.ofSeconds(8);
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) timeout.toMillis());
        fabrica.setReadTimeout((int) timeout.toMillis());
        return builder.requestFactory(fabrica).build();
    }
}
