package com.innovax.ganadera.sync.dto;

import java.util.List;
import java.util.UUID;

import com.innovax.ganadera.common.error.ApiError;

/**
 * Cómo terminó cada operación del push.
 *
 * <ul>
 *   <li>{@code APLICADO}: listo (o ya estaba aplicada: reenviarla no la repite). El celular la saca
 *       de pendientes.</li>
 *   <li>{@code RECHAZADO}: no se puede aplicar (datos inválidos, el animal ya se vendió, sin
 *       permiso...). Reintentar no sirve: el celular la muestra con {@code mensaje} para que se
 *       corrija o se descarte.</li>
 *   <li>{@code ERROR}: falla inesperada del servidor. El celular la reintenta más tarde.</li>
 * </ul>
 *
 * @param operacionId en eventos, el {@code operacionId} con el que quedaron registrados
 * @param descartados en {@code ACTUALIZAR_ANIMAL}, campos que no se aplicaron porque en el servidor
 *     había un cambio más nuevo (last-write-wins por campo)
 */
public record ResultadoOperacion(
    UUID id,
    Estado estado,
    String mensaje,
    List<ApiError.FieldError> errores,
    UUID operacionId,
    List<String> aplicados,
    List<String> descartados
) {

    public enum Estado {
        APLICADO, RECHAZADO, ERROR
    }

    public static ResultadoOperacion aplicado(UUID id) {
        return new ResultadoOperacion(id, Estado.APLICADO, null, null, null, null, null);
    }

    public static ResultadoOperacion evento(UUID id, UUID operacionId) {
        return new ResultadoOperacion(id, Estado.APLICADO, null, null, operacionId, null, null);
    }

    public static ResultadoOperacion campos(UUID id, List<String> aplicados, List<String> descartados) {
        return new ResultadoOperacion(id, Estado.APLICADO, null, null, null, aplicados, descartados);
    }

    public static ResultadoOperacion rechazado(UUID id, String mensaje, List<ApiError.FieldError> errores) {
        return new ResultadoOperacion(id, Estado.RECHAZADO, mensaje, errores, null, null, null);
    }

    public static ResultadoOperacion error(UUID id, String mensaje) {
        return new ResultadoOperacion(id, Estado.ERROR, mensaje, null, null, null, null);
    }
}
