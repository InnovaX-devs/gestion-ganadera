package com.innovax.ganadera.plan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.innovax.ganadera.cobro.dto.CobroResponse;
import com.innovax.ganadera.plan.Plan;

/**
 * El plan de la organización y cuánto usa de cada límite.
 *
 * @param plan        el que rige hoy (durante la prueba, el de prueba)
 * @param contratado  el plan de la organización, que rige cuando termina la prueba
 * @param pruebaHasta último día de la prueba (puede ser pasado: la prueba ya terminó); null si nunca tuvo
 * @param diasDePrueba días que quedan, contando hoy (0 si no está en prueba)
 * @param planes      todos, de menor a mayor, para comparar
 * @param cobro       suscripción con Mercado Pago
 */
public record PlanResponse(
    PlanInfo plan,
    PlanInfo contratado,
    boolean enPrueba,
    LocalDate pruebaHasta,
    int diasDePrueba,
    Uso uso,
    List<PlanInfo> planes,
    CobroResponse cobro
) {

    /** Límites null: sin límite. Precio null o 0: no se contrata online. */
    public record PlanInfo(String codigo, String nombre, Integer maxAnimales, Integer maxEstablecimientos,
                           Integer maxUsuarios, BigDecimal precioMensual) {

        public static PlanInfo de(Plan p) {
            return new PlanInfo(p.getCodigo(), p.getNombre(), p.getMaxAnimales(), p.getMaxEstablecimientos(),
                p.getMaxUsuarios(), p.getPrecioMensual());
        }
    }

    /**
     * @param animales               activos en toda la organización
     * @param invitacionesPendientes ocupan lugar de usuario hasta que se aceptan, vencen o se revocan
     */
    public record Uso(long animales, long establecimientos, long usuarios, long invitacionesPendientes) {
    }
}
