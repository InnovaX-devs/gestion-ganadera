package com.innovax.ganadera.evento.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.innovax.ganadera.evento.TipoEvento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Registrar un evento.
 *
 * <pre>
 * { "tipo": "OBSERVACION", "fecha": "2026-10-29",
 *   "animalIds": ["..."],            // o "loteId", o ninguno (evento del establecimiento)
 *   "datos": { "texto": "Rengueando de la pata trasera" } }
 * </pre>
 *
 * <p>En los tipos que son siempre sobre animales (ej: tratamiento) se puede mandar {@code loteId} o
 * {@code rodeoId} en vez de {@code animalIds}: se registra un evento por cada animal activo del
 * lote o rodeo.
 *
 * @param operacionId opcional. Si lo manda el cliente y ya existe, no se registra de nuevo (sirve
 *                    para que la app offline pueda reintentar sin duplicar).
 * @param datos       lo propio del tipo; su formato lo define el record de datos de cada tipo.
 */
public record EventoRequest(
    UUID operacionId,

    @NotNull(message = "Elegí el tipo de evento")
    TipoEvento tipo,

    @NotNull(message = "Indicá la fecha")
    @PastOrPresent(message = "La fecha no puede ser futura")
    LocalDate fecha,

    @Size(max = 500, message = "Se pueden registrar hasta 500 animales por vez")
    List<UUID> animalIds,

    UUID loteId,

    UUID rodeoId,

    JsonNode datos
) {

    public List<UUID> animalIdsONada() {
        return animalIds == null ? List.of() : animalIds;
    }
}
