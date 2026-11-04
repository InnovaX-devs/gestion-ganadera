package com.innovax.ganadera.calendario.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alertas sanitarias del establecimiento (#34), al día de hoy.
 *
 * <p>Las alertas de partos próximos todavía no están: la fecha probable de parto sale del servicio o
 * del tacto, que todavía no se registran (ver docs/calendario-sanitario.md).
 *
 * @param tratamientosVencidos planificados pendientes con fecha anterior a hoy
 * @param tratamientosProximos planificados pendientes desde hoy hasta {@code diasProximos} días
 */
public record AlertasResponse(
    LocalDate hoy,
    int diasProximos,
    List<PlanResponse> tratamientosVencidos,
    List<PlanResponse> tratamientosProximos,
    Carencias carencias
) {

    /**
     * @param animalesEnCarencia animales activos que hoy no se pueden vender ni faenar
     * @param salenProximamente los que quedan libres en los próximos días, el más cercano primero
     */
    public record Carencias(int animalesEnCarencia, List<AnimalCarencia> salenProximamente) {
    }

    /** {@code libreDesde}: desde ese día ya se puede vender o faenar. */
    public record AnimalCarencia(UUID animalId, String caravana, LocalDate libreDesde, String productos) {
    }

    public int total() {
        return tratamientosVencidos.size() + tratamientosProximos.size() + carencias.salenProximamente().size();
    }
}
