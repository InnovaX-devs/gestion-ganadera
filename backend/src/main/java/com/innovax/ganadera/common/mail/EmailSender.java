package com.innovax.ganadera.common.mail;

/**
 * Manda emails de texto plano. Según {@code app.mail.enabled} y {@code app.mail.proveedor} van al log,
 * por SMTP o por la API de Resend.
 */
public interface EmailSender {

    void enviar(String destinatario, String asunto, String texto);
}
