package com.innovax.ganadera.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.innovax.ganadera.common.mail.EmailSender;

/**
 * Reemplaza al envío de emails en los tests y guarda lo que se "mandó". Las invitaciones llevan el
 * token solo en el email (la API nunca lo devuelve), así que los tests lo sacan de acá.
 */
@Component
@Primary
public class FakeEmailSender implements EmailSender {

    public record Mensaje(String destinatario, String asunto, String texto) {
    }

    private static final Pattern LINK = Pattern.compile("/(?:invitacion|activar)/([A-Za-z0-9_-]+)");

    private final List<Mensaje> mensajes = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
        mensajes.add(new Mensaje(destinatario, asunto, texto));
    }

    public List<Mensaje> mensajes() {
        return List.copyOf(mensajes);
    }

    public Mensaje ultimo() {
        return mensajes.getLast();
    }

    /** Token del link del último email enviado. */
    public String ultimoToken() {
        Matcher matcher = LINK.matcher(ultimo().texto());
        if (!matcher.find()) {
            throw new IllegalStateException("El último email no tiene un link de invitación");
        }
        return matcher.group(1);
    }

    public void limpiar() {
        mensajes.clear();
    }
}
