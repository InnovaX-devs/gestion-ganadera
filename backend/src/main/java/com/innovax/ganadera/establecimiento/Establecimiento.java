package com.innovax.ganadera.establecimiento;

import java.math.BigDecimal;

import com.innovax.ganadera.common.tenant.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un campo de la organización. Los lotes, rodeos y animales cuelgan de acá. */
@Getter
@Setter
@Entity
@Table(name = "establecimiento")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Establecimiento extends TenantEntity {

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 100)
    private String localidad;

    @Column(length = 100)
    private String provincia;

    @Column(name = "superficie_ha", precision = 12, scale = 2)
    private BigDecimal superficieHa;

    /** Registro Nacional Sanitario de Productores Agropecuarios (SENASA). */
    @Column(length = 30)
    private String renspa;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitud;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean activo = true;

    public Establecimiento(String nombre) {
        this.nombre = nombre;
    }

    public void darDeBaja() {
        this.activo = false;
    }
}
