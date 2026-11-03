package com.innovax.ganadera.invitacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens de los links de invitación (y de activación de cuentas, #35): se generan al azar y en la
 * base solo se guarda su hash.
 */
public final class TokensInvitacion {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokensInvitacion() {
    }

    /** 256 bits al azar, en base64 apto para URL (43 caracteres). Imposible de adivinar. */
    public static String generar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
