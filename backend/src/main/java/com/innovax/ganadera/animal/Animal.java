package com.innovax.ganadera.animal;

import java.time.LocalDate;
import java.util.Locale;

import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.Rodeo;
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
 * Un animal del rodeo. Las reglas que cruzan datos (categoría vs sexo, madre hembra, lote del mismo
 * establecimiento, caravana única) se validan en {@link AnimalService}; acá solo se guardan.
 */
@Getter
@Setter
@Entity
@Table(name = "animal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Animal extends TenantEntity {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodeo_id")
    private Rodeo rodeo;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 30)
    private String caravana;

    @Setter(AccessLevel.NONE)
    @Column(name = "caravana_electronica", length = 30)
    private String caravanaElectronica;

    @Convert(converter = Sexo.Converter.class)
    @Column(nullable = false, length = 10)
    private Sexo sexo;

    @Convert(converter = Categoria.Converter.class)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @Column(length = 50)
    private String raza;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_nacimiento_estimada", nullable = false)
    private boolean fechaNacimientoEstimada;

    @Column(nullable = false)
    private boolean castrado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "madre_id")
    private Animal madre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private Animal padre;

    @Convert(converter = EstadoAnimal.Converter.class)
    @Column(nullable = false, length = 10)
    private EstadoAnimal estado = EstadoAnimal.ACTIVO;

    @Column(length = 500)
    private String observaciones;

    public Animal(Establecimiento establecimiento, String caravana, Sexo sexo, Categoria categoria) {
        this.establecimiento = establecimiento;
        setCaravana(caravana);
        this.sexo = sexo;
        this.categoria = categoria;
    }

    /** Las caravanas se guardan sin espacios y en mayúsculas, para buscarlas y compararlas fácil. */
    public void setCaravana(String caravana) {
        this.caravana = normalizarCaravana(caravana);
    }

    public void setCaravanaElectronica(String caravanaElectronica) {
        this.caravanaElectronica = normalizarCaravana(caravanaElectronica);
    }

    public boolean isActivo() {
        return estado == EstadoAnimal.ACTIVO;
    }

    public static String normalizarCaravana(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }
}
