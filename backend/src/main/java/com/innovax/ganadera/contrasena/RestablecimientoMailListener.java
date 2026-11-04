package com.innovax.ganadera.contrasena;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.innovax.ganadera.common.mail.EmailSender;
import com.innovax.ganadera.invitacion.InvitacionProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Manda el link para elegir una contraseña nueva, después de confirmar la transacción. */
@Slf4j
@Component
@RequiredArgsConstructor
class RestablecimientoMailListener {

    private static final DateTimeFormatter HORA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final EmailSender emailSender;
    private final InvitacionProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(RestablecimientoEnviadoEvent evento) {
        String link = properties.webUrl() + "/restablecer/" + evento.token();
        String motivo = evento.pedidoPorInnovax()
            ? "El equipo de Innovax te mandó este enlace para que elijas una contraseña nueva."
            : "Pediste cambiar la contraseña de tu cuenta de Gestión Ganadera.";
        String texto = """
            Hola %s,

            %s

            Elegí la contraseña nueva desde este enlace:
            %s

            El enlace sirve una sola vez y vence el %s. Si no lo pediste, ignorá este email: tu contraseña no cambia.
            """.formatted(evento.nombre(), motivo, link, HORA.format(evento.venceAt()));
        try {
            emailSender.enviar(evento.destinatario(), "Cambiá tu contraseña de Gestión Ganadera", texto);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el enlace para cambiar la contraseña a {}", evento.destinatario(), e);
        }
    }
}
