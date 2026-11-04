package com.innovax.ganadera.cobro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.common.tenant.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Suscripción mensual de una organización a un plan, cobrada por Mercado Pago (#35).
 *
 * <p>Cuándo deja de regir el plan: {@link #limite(int)}. Con la suscripción activa o pausada hay
 * días de gracia después de lo pago; cancelada, rige solo hasta el último día pago.
 */
@Getter
@Setter
@Entity
@Table(name = "suscripcion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suscripcion extends BaseEntity {

    @Column(name = "organizacion_id", nullable = false, updatable = false)
    private UUID organizacionId;

    @Column(name = "plan_codigo", nullable = false, updatable = false, length = 20)
    private String planCodigo;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado = EstadoSuscripcion.PENDIENTE;

    @Column(name = "mp_id", length = 64)
    private String mpId;

    @Column(name = "init_point", length = 500)
    private String initPoint;

    @Column(name = "activada_el")
    private LocalDate activadaEl;

    @Column(name = "pagado_hasta")
    private LocalDate pagadoHasta;

    /** Día del primer cobro, si se suscribió durante la prueba o un pago manual. Null: enseguida. */
    @Column(name = "primer_cobro")
    private LocalDate primerCobro;

    public Suscripcion(UUID organizacionId, String planCodigo, BigDecimal monto) {
        this.organizacionId = organizacionId;
        this.planCodigo = planCodigo;
        this.monto = monto;
    }

    /** Rige el plan (aunque esté en gracia o cancelada con días pagos). */
    public boolean vigente() {
        return estado == EstadoSuscripcion.ACTIVA || estado == EstadoSuscripcion.PAUSADA
            || estado == EstadoSuscripcion.CANCELADA;
    }

    /**
     * Último día pago. Si todavía no se aprobó ningún cobro: el día antes del primer cobro (si se
     * suscribió durante la prueba), o el de la activación.
     */
    public LocalDate base() {
        if (pagadoHasta != null) return pagadoHasta;
        if (activadaEl == null) return null;
        // Hasta el primer cobro está cubierta (la prueba o el pago manual que tenía)
        LocalDate antesDelPrimerCobro = primerCobro == null ? null : primerCobro.minusDays(1);
        return antesDelPrimerCobro != null && antesDelPrimerCobro.isAfter(activadaEl) ? antesDelPrimerCobro : activadaEl;
    }

    /** Último día en que rige el plan. Null si nunca se activó. */
    public LocalDate limite(int diasGracia) {
        LocalDate base = base();
        if (base == null) return null;
        return estado == EstadoSuscripcion.CANCELADA ? base : base.plusDays(diasGracia);
    }

    /** No se pudo cobrar y está corriendo la gracia. */
    public boolean enGracia(LocalDate hoy, int diasGracia) {
        if (estado != EstadoSuscripcion.ACTIVA && estado != EstadoSuscripcion.PAUSADA) return false;
        LocalDate base = base();
        return base != null && hoy.isAfter(base) && !hoy.isAfter(limite(diasGracia));
    }

    /** Se terminó lo pago y la gracia. */
    public boolean vencida(LocalDate hoy, int diasGracia) {
        LocalDate limite = limite(diasGracia);
        return vigente() && limite != null && hoy.isAfter(limite);
    }

    /** Registra un mes pago: el plan rige hasta {@code hasta} (si es más que lo que ya tenía). */
    public void pagadoHasta(LocalDate hasta) {
        if (hasta != null && (pagadoHasta == null || hasta.isAfter(pagadoHasta))) {
            this.pagadoHasta = hasta;
        }
    }
}
