package com.innovax.ganadera.animal;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

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

    /** Solo cambia con un Movimiento entre establecimientos (#22): ver {@link #trasladarA}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false)
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

    /**
     * Evento Nacimiento que creó este animal (#18). Null si se cargó a mano. Si ese evento se anula,
     * el animal se borra.
     */
    @Column(name = "evento_nacimiento_id", updatable = false)
    private UUID eventoNacimientoId;

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

    /**
     * Pasa el animal a otro establecimiento (el que registra el evento Movimiento controla que sea
     * de la misma organización). El lote y el rodeo eran del establecimiento anterior, así que
     * quedan sin asignar: quien llama les pone los nuevos si corresponde.
     */
    public void trasladarA(Establecimiento destino) {
        this.establecimiento = destino;
        this.lote = null;
        this.rodeo = null;
    }

    public static String normalizarCaravana(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }
}
