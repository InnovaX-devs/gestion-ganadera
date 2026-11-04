package com.innovax.ganadera.calendario.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.calendario.EstadoPlan;
import com.innovax.ganadera.calendario.TratamientoPlanificado;

/**
 * @param destino a quién se aplica: LOTE, RODEO o ESTABLECIMIENTO (todo el campo)
 * @param vencido pendiente con fecha anterior a hoy
 */
public record PlanResponse(
    UUID id,
    LocalDate fecha,
    Destino destino,
    Referencia producto,
    String descripcion,
    String dosis,
    String notas,
    int avisarDiasAntes,
    EstadoPlan estado,
    boolean vencido,
    UUID realizadoOperacionId,
    Instant realizadoEn,
    String creadoPor
) {

    public record Destino(String tipo, UUID id, String nombre) {
    }

    public record Referencia(UUID id, String nombre) {
    }

    public static PlanResponse from(TratamientoPlanificado p, LocalDate hoy) {
        Destino destino = p.getLote() != null
            ? new Destino("LOTE", p.getLote().getId(), p.getLote().getNombre())
            : p.getRodeo() != null
                ? new Destino("RODEO", p.getRodeo().getId(), p.getRodeo().getNombre())
                : new Destino("ESTABLECIMIENTO", null, null);
        var producto = p.getProducto() == null ? null
            : new Referencia(p.getProducto().getId(), p.getProducto().getNombre());
        var creador = p.getCreadoPor();
        String creadoPor = creador == null ? null
            : creador.getApellido() == null ? creador.getNombre() : creador.getNombre() + " " + creador.getApellido();
        return new PlanResponse(p.getId(), p.getFecha(), destino, producto, p.getDescripcion(), p.getDosis(),
            p.getNotas(), p.getAvisarDiasAntes(), p.getEstado(), p.esPendiente() && p.getFecha().isBefore(hoy),
            p.getRealizadoOperacionId(), p.getRealizadoEn(), creadoPor);
    }
}
