package com.innovax.ganadera.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa los jobs programados ({@code @Scheduled}). Se apagan con {@code app.jobs.habilitados=false}
 * (los tests lo hacen y llaman a los jobs a mano).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.jobs.habilitados", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
