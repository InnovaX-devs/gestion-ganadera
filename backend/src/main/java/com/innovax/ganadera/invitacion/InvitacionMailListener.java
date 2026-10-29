package com.innovax.ganadera.invitacion;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.innovax.ganadera.common.mail.EmailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manda el email de la invitación después de confirmar la transacción: si el correo falla, la
 * invitación igual queda guardada y el dueño puede reenviarla. Y si la transacción falla, no sale
 * ningún email de una invitación que no existe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class InvitacionMailListener {

    private static final DateTimeFormatter FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final EmailSender emailSender;
    private final InvitacionProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(InvitacionEnviadaEvent evento) {
        String link = properties.webUrl() + "/invitacion/" + evento.token();
        String asunto = evento.invitadoPor() + " te invitó a \"" + evento.establecimiento() + "\" en Gestión Ganadera";
        String texto = """
            Hola,

            %s te invitó a sumarte al establecimiento "%s" en Gestión Ganadera, con el rol de %s.

            Aceptá la invitación desde este enlace:
            %s

            El enlace vence el %s. Si no esperabas esta invitación, ignorá este mensaje.
            """.formatted(evento.invitadoPor(), evento.establecimiento(), evento.rol().etiqueta(), link,
            FECHA.format(evento.venceAt()));
        try {
            emailSender.enviar(evento.destinatario(), asunto, texto);
        } catch (RuntimeException e) {
            // La invitación ya está guardada: se puede reenviar desde la pantalla de invitaciones
            log.error("No se pudo enviar la invitación a {}", evento.destinatario(), e);
        }
    }
}
