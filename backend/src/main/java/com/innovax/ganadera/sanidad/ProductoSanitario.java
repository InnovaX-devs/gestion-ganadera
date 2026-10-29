package com.innovax.ganadera.sanidad;

import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;

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
 * Un producto del catálogo del establecimiento (ej: "Ivomec", "Aftosa"). Se elige al registrar un
 * tratamiento; los valores habituales (dosis, vía, carencia) son solo sugerencias para el formulario.
 */
@Getter
@Setter
@Entity
@Table(name = "producto_sanitario")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductoSanitario extends TenantEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Convert(converter = TipoProducto.Converter.class)
    @Column(nullable = false, length = 20)
    private TipoProducto tipo;

    @Column(name = "dias_carencia", nullable = false)
    private int diasCarencia;

    @Column(name = "dosis_habitual", length = 100)
    private String dosisHabitual;

    @Convert(converter = ViaAdministracion.Converter.class)
    @Column(name = "via_habitual", length = 20)
    private ViaAdministracion viaHabitual;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean activo = true;

    public ProductoSanitario(Establecimiento establecimiento, String nombre, TipoProducto tipo) {
        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public void darDeBaja() {
        this.activo = false;
    }
}
