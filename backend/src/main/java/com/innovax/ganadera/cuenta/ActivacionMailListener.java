package com.innovax.ganadera.cuenta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.innovax.ganadera.common.mail.EmailSender;
import com.innovax.ganadera.invitacion.InvitacionProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Manda el email de activación después de confirmar la transacción (como las invitaciones). */
@Slf4j
@Component
@RequiredArgsConstructor
class ActivacionMailListener {

    private static final DateTimeFormatter FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final EmailSender emailSender;
    private final InvitacionProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(ActivacionEnviadaEvent evento) {
        String link = properties.webUrl() + "/activar/" + evento.token();
        String texto = """
            Hola %s,

            Te creamos la cuenta "%s" en Gestión Ganadera.

            Elegí tu contraseña desde este enlace para empezar a usarla:
            %s

            El enlace vence el %s. Después, ingresás siempre con este email y tu contraseña.
            """.formatted(evento.nombre(), evento.organizacion(), link, FECHA.format(evento.venceAt()));
        try {
            emailSender.enviar(evento.destinatario(), "Tu cuenta de Gestión Ganadera está lista", texto);
        } catch (RuntimeException e) {
            // Se puede reenviar desde el panel de administración
            log.error("No se pudo enviar la activación a {}", evento.destinatario(), e);
        }
    }
}
