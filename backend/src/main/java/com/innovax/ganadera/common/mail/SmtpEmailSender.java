package com.innovax.ganadera.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Manda los emails por SMTP. Se activa con {@code app.mail.enabled=true} y {@code app.mail.proveedor=smtp}
 * (el valor por defecto), más la config {@code spring.mail.*}.
 */
@Component
@ConditionalOnExpression("${app.mail.enabled:false} and '${app.mail.proveedor:smtp}' != 'resend'")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String remitente;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        mailSender.send(mensaje);
    }
}
