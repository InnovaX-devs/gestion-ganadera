package com.innovax.ganadera.agrupacion;

import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base común de {@link Lote} y {@link Rodeo}: un grupo de animales dentro de un establecimiento.
 * Hoy tienen los mismos datos; si más adelante divergen, cada subclase agrega los suyos.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Agrupacion extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Setter
    @Column(nullable = false, length = 100)
    private String nombre;

    @Setter
    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    protected Agrupacion(Establecimiento establecimiento, String nombre) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
    }

    public void darDeBaja() {
        this.activo = false;
    }
}
