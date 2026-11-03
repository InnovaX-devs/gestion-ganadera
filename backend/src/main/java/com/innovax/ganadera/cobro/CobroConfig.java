package com.innovax.ganadera.cobro;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CobroProperties.class)
public class CobroConfig {

    @Bean
    RestClient mercadoPagoRestClient(RestClient.Builder builder, CobroProperties propiedades) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) propiedades.timeout().toMillis());
        fabrica.setReadTimeout((int) propiedades.timeout().toMillis());
        return builder.baseUrl(propiedades.mpApiUrl()).requestFactory(fabrica).build();
    }
}
