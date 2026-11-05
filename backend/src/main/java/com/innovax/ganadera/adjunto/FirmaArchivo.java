package com.innovax.ganadera.adjunto;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Qué archivo es, mirando sus primeros bytes. No se confía en el nombre ni en el Content-Type que
 * manda el cliente: un .exe renombrado a .jpg tiene que rechazarse.
 */
final class FirmaArchivo {

    /** Cuántos bytes hacen falta para reconocer cualquiera de los formatos. */
    static final int BYTES_NECESARIOS = 16;

    private static final Set<String> MARCAS_HEIC =
        Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private FirmaArchivo() {
    }

    /** Tipo de contenido (jpeg, png, webp, heic o pdf), o null si no es ninguno de los permitidos. */
    static String detectar(byte[] c) {
        if (empieza(c, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (empieza(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (c.length >= 12 && texto(c, 0, 4).equals("RIFF") && texto(c, 8, 12).equals("WEBP")) {
            return "image/webp";
        }
        if (c.length >= 12 && texto(c, 4, 8).equals("ftyp") && MARCAS_HEIC.contains(texto(c, 8, 12))) {
            return "image/heic";
        }
        if (c.length >= 5 && texto(c, 0, 5).equals("%PDF-")) {
            return "application/pdf";
        }
        return null;
    }

    private static boolean empieza(byte[] c, int... esperado) {
        if (c.length < esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((c[i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    private static String texto(byte[] c, int desde, int hasta) {
        return new String(c, desde, hasta - desde, StandardCharsets.ISO_8859_1);
    }
}
