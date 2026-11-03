package com.innovax.ganadera.calendario;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.sanidad.ProductoSanitario;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un tratamiento planificado para una fecha (#34): de un lote, de un rodeo o de todo el
 * establecimiento (sin lote ni rodeo). Cuando se hace, queda REALIZADO; si no se hace, sigue
 * PENDIENTE y aparece como vencido.
 */
@Getter
@Setter
@Entity
@Table(name = "tratamiento_planificado")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TratamientoPlanificado extends TenantEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodeo_id")
    private Rodeo rodeo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoSanitario producto;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(length = 100)
    private String dosis;

    @Column(length = 500)
    private String notas;

    @Column(name = "avisar_dias_antes", nullable = false)
    private int avisarDiasAntes = 1;

    @Setter(AccessLevel.NONE)
    @Convert(converter = EstadoPlan.Converter.class)
    @Column(nullable = false, length = 12)
    private EstadoPlan estado = EstadoPlan.PENDIENTE;

    @Setter(AccessLevel.NONE)
    @Column(name = "realizado_operacion_id")
    private UUID realizadoOperacionId;

    @Setter(AccessLevel.NONE)
    @Column(name = "realizado_en")
    private Instant realizadoEn;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por", updatable = false)
    private Usuario creadoPor;

    public TratamientoPlanificado(Establecimiento establecimiento, Usuario creadoPor) {
        this.establecimiento = establecimiento;
        this.creadoPor = creadoPor;
    }

    public boolean esPendiente() {
        return estado == EstadoPlan.PENDIENTE;
    }

    /** Se hizo. {@code operacionId}: el tratamiento registrado, si se registró desde el calendario. */
    public void marcarRealizado(UUID operacionId, Instant cuando) {
        this.estado = EstadoPlan.REALIZADO;
        this.realizadoOperacionId = operacionId;
        this.realizadoEn = cuando;
    }

    public void cancelar() {
        this.estado = EstadoPlan.CANCELADO;
    }

    /** Vuelve a pendiente (ej: se marcó como hecho por error). */
    public void reabrir() {
        this.estado = EstadoPlan.PENDIENTE;
        this.realizadoOperacionId = null;
        this.realizadoEn = null;
    }
}
