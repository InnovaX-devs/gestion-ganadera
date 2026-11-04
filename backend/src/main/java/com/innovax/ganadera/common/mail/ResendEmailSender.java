package com.innovax.ganadera.common.mail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Manda los emails por la API HTTP de Resend (puerto 443) en vez de SMTP. Hace falta en hostings
 * que bloquean los puertos SMTP, como el plan gratis de Render. Se activa con
 * {@code app.mail.enabled=true} y {@code app.mail.proveedor=resend}.
 */
@Component
@ConditionalOnExpression("${app.mail.enabled:false} and '${app.mail.proveedor:smtp}' == 'resend'")
public class ResendEmailSender implements EmailSender {

    private final RestClient http;
    private final String apiKey;
    private final String remitente;

    @Autowired
    public ResendEmailSender(RestClient.Builder builder,
                             @Value("${app.mail.resend.api-url:https://api.resend.com}") String apiUrl,
                             @Value("${app.mail.resend.api-key}") String apiKey,
                             @Value("${app.mail.from}") String remitente,
                             @Value("${app.mail.timeout:10s}") Duration timeout) {
        this(builder.baseUrl(apiUrl).requestFactory(fabrica(timeout)).build(), apiKey, remitente);
    }

    ResendEmailSender(RestClient http, String apiKey, String remitente) {
        this.http = http;
        this.apiKey = apiKey;
        this.remitente = remitente;
    }

    private static SimpleClientHttpRequestFactory fabrica(Duration timeout) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout((int) timeout.toMillis());
        fabrica.setReadTimeout((int) timeout.toMillis());
        return fabrica;
    }

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
        Map<String, Object> body = Map.of(
            "from", remitente,
            "to", List.of(destinatario),
            "subject", asunto,
            "text", texto);
        try {
            http.post().uri("/emails")
                .headers(h -> h.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Resend explica el motivo en el cuerpo (ej. dominio del remitente sin verificar)
            throw new IllegalStateException("Resend rechazó el email (" + e.getStatusCode().value() + "): "
                + e.getResponseBodyAsString(), e);
        }
    }
}
