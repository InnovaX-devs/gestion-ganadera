package com.innovax.ganadera.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Para desarrollo: no manda nada, escribe el email en el log. Así se puede probar una invitación
 * copiando el link de la consola, sin configurar un servidor de correo.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
        log.info("Email NO enviado (app.mail.enabled=false)\nPara: {}\nAsunto: {}\n\n{}", destinatario, asunto, texto);
    }
}
