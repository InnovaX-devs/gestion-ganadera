package com.innovax.ganadera.animal.categoria;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ParametrosCategoria.class)
public class CategoriaConfig {

    /** Las edades y el "hoy" del job se calculan con la hora de Argentina, no con la del servidor. */
    public static final String ZONA_HORARIA = "America/Argentina/Buenos_Aires";

    @Bean
    ReglasCategoria reglasCategoria(ParametrosCategoria parametros) {
        return new ReglasCategoria(parametros);
    }

    /** Reloj inyectable: en los tests se puede reemplazar por uno fijo. */
    @Bean
    @ConditionalOnMissingBean
    Clock reloj() {
        return Clock.system(ZoneId.of(ZONA_HORARIA));
    }
}
