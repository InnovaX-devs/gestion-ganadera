package com.innovax.ganadera.cobro;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisos de Mercado Pago (#35). Es público: lo llama Mercado Pago. Se configura en Tus integraciones →
 * Webhooks con la URL {@code https://<backend>/api/mercadopago/webhook} y los eventos de suscripciones.
 *
 * <p>Si hay clave secreta (MP_WEBHOOK_SECRET) se valida la firma. Igual, el aviso nunca se toma como
 * verdad: solo dispara una consulta a Mercado Pago con nuestro Access Token.
 */
@Slf4j
@RestController
@RequestMapping("/api/mercadopago/webhook")
@RequiredArgsConstructor
public class WebhookMercadoPagoController {

    private final SuscripcionService service;
    private final CobroProperties propiedades;

    @PostMapping
    public ResponseEntity<Void> recibir(@RequestParam(name = "type", required = false) String tipoQuery,
                                        @RequestParam(name = "topic", required = false) String topic,
                                        @RequestParam(name = "data.id", required = false) String idQuery,
                                        @RequestHeader(name = "x-signature", required = false) String firma,
                                        @RequestHeader(name = "x-request-id", required = false) String requestId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String tipo = primero(tipoQuery, topic, body == null ? null : texto(body.get("type")));
        String id = primero(idQuery, body != null && body.get("data") instanceof Map<?, ?> data ? texto(data.get("id")) : null);
        if (id == null) return ResponseEntity.ok().build();

        if (!propiedades.mpWebhookSecret().isEmpty()
            && !FirmaWebhook.valida(propiedades.mpWebhookSecret(), id, requestId, firma)) {
            log.warn("Mercado Pago: aviso con firma inválida (tipo {}, id {})", tipo, id);
            return ResponseEntity.status(401).build();
        }
        // Si Mercado Pago no responde, devolvemos error para que reintente el aviso más tarde
        service.procesarAviso(tipo, id);
        return ResponseEntity.ok().build();
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static String primero(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
