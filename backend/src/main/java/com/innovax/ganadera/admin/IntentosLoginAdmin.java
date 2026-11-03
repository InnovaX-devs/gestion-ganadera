package com.innovax.ganadera.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Freno contra adivinar la contraseña del panel: después de {@value #MAXIMO} intentos fallidos
 * seguidos, el login queda cerrado {@link #BLOQUEO} para todos. Es una sola contraseña compartida,
 * así que se cuenta en total y no por email. Vive en memoria: reiniciar el backend lo limpia.
 */
@Component
@RequiredArgsConstructor
public class IntentosLoginAdmin {

    static final int MAXIMO = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final Clock reloj;

    private int fallidos;
    private Instant bloqueadoHasta;

    public synchronized boolean bloqueado() {
        if (bloqueadoHasta != null && reloj.instant().isAfter(bloqueadoHasta)) {
            reiniciar();
        }
        return bloqueadoHasta != null;
    }

    public synchronized void fallo() {
        fallidos++;
        if (fallidos >= MAXIMO) {
            bloqueadoHasta = reloj.instant().plus(BLOQUEO);
        }
    }

    public synchronized void reiniciar() {
        fallidos = 0;
        bloqueadoHasta = null;
    }
}
