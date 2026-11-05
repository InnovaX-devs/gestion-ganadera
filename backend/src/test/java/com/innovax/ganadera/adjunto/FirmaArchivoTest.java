package com.innovax.ganadera.adjunto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Issue #43: el tipo sale de los primeros bytes, no del nombre ni de lo que diga el cliente. */
class FirmaArchivoTest {

    @Test
    void reconoceLosFormatosPermitidos() {
        assertEquals("image/jpeg", FirmaArchivo.detectar(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 16)));
        assertEquals("image/png", FirmaArchivo.detectar(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)));
        assertEquals("image/webp", FirmaArchivo.detectar("RIFF\u0010\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("image/heic", FirmaArchivo.detectar(conTexto(4, "ftypheic")));
        assertEquals("image/heic", FirmaArchivo.detectar(conTexto(4, "ftypmif1")));
        assertEquals("application/pdf", FirmaArchivo.detectar("%PDF-1.7\n".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void rechazaTodoLoDemas() {
        assertNull(FirmaArchivo.detectar("MZ\u0090\u0000\u0003".getBytes(StandardCharsets.ISO_8859_1)), "ejecutable de Windows");
        assertNull(FirmaArchivo.detectar("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertNull(FirmaArchivo.detectar("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)));
        assertNull(FirmaArchivo.detectar(conTexto(4, "ftypisom")), "un mp4 no es una foto");
        assertNull(FirmaArchivo.detectar(new byte[0]));
        assertNull(FirmaArchivo.detectar(bytes(0xFF, 0xD8)), "jpeg cortado");
    }

    private static byte[] bytes(int... valores) {
        byte[] resultado = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            resultado[i] = (byte) valores[i];
        }
        return resultado;
    }

    /** Cuatro bytes de largo de caja y después el texto, como un archivo ISO-BMFF. */
    private static byte[] conTexto(int desde, String texto) {
        byte[] resultado = new byte[desde + texto.length() + 4];
        System.arraycopy(texto.getBytes(StandardCharsets.ISO_8859_1), 0, resultado, desde, texto.length());
        return resultado;
    }
}
