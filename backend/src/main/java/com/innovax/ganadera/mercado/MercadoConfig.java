package com.innovax.ganadera.mercado;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MercadoProperties.class)
public class MercadoConfig {

    /**
     * Cliente HTTP para las fuentes externas, con timeout: una fuente colgada no puede trabar el job.
     */
    @Bean
    RestClient mercadoRestClient(RestClient.Builder builder, MercadoProperties propiedades) {
        Duration timeout = propiedades.timeout() != null ? propiedades.timeout() : Duration.ofSeconds(15);
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) timeout.toMillis());
        fabrica.setReadTimeout((int) timeout.toMillis());
        return builder
            .requestFactory(fabrica)
            .defaultHeader(HttpHeaders.USER_AGENT, "GestionGanadera/1.0 (cotizaciones diarias)")
            .build();
    }
}
