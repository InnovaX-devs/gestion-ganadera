package com.innovax.ganadera.common.mail;

/** Manda emails de texto plano. Hay dos implementaciones, según {@code app.mail.enabled}. */
public interface EmailSender {

    void enviar(String destinatario, String asunto, String texto);
}
