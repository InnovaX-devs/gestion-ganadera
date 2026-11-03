package com.innovax.ganadera.cobro;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.animal.categoria.CategoriaConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Una vez por día: las suscripciones sin días pagos ni de gracia pasan a VENCIDA y la cuenta queda
 * sin uso pago: solo lectura (#35). Horario: {@code app.jobs.suscripciones-cron} (por defecto a las 4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuscripcionesJob {

    private final SuscripcionService service;
    private final Clock reloj;

    @Scheduled(cron = "${app.jobs.suscripciones-cron:0 0 4 * * *}", zone = CategoriaConfig.ZONA_HORARIA)
    public void ejecutarDiario() {
        int vencidas = service.vencer(LocalDate.now(reloj));
        log.info("Suscripciones: {} vencidas", vencidas);
    }
}
