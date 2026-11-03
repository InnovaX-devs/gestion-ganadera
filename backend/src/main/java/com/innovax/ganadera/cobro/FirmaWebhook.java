package com.innovax.ganadera.cobro;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma de los webhooks de Mercado Pago. El header {@code x-signature} trae {@code ts=...,v1=...};
 * {@code v1} es el HMAC-SHA256 (hex) de {@code id:[data.id];request-id:[x-request-id];ts:[ts];}
 * con la clave secreta de la aplicación.
 */
public final class FirmaWebhook {

    private FirmaWebhook() {
    }

    public static boolean valida(String secreto, String dataId, String requestId, String xSignature) {
        if (xSignature == null || dataId == null) return false;
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].trim().equals("ts")) ts = kv[1].trim();
            if (kv[0].trim().equals("v1")) v1 = kv[1].trim();
        }
        if (ts == null || v1 == null) return false;
        String esperada = firmar(secreto, dataId, requestId, ts);
        return MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8),
            v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC del manifiesto. Público para que los tests puedan firmar avisos. */
    public static String firmar(String secreto, String dataId, String requestId, String ts) {
        // Si el id es alfanumérico, Mercado Pago lo firma en minúsculas
        String manifiesto = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + (requestId == null ? "" : requestId)
            + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
