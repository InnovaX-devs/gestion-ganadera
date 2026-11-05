package com.innovax.ganadera.adjunto.dto;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.adjunto.Adjunto;

/**
 * Un adjunto, sin el contenido: para verlo se pide {@code GET .../adjuntos/{id}} (necesita el token,
 * por eso la web lo baja con el cliente HTTP y no con un {@code <img src>} directo).
 */
public record AdjuntoResponse(
    UUID id,
    String nombre,
    String tipoContenido,
    long tamanio,
    Instant createdAt
) {

    public static AdjuntoResponse de(Adjunto a) {
        return new AdjuntoResponse(a.getId(), a.getNombre(), a.getTipoContenido(), a.getTamanio(), a.getCreatedAt());
    }
}
