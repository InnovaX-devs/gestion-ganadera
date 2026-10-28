package com.innovax.ganadera.agrupacion;

import com.innovax.ganadera.establecimiento.Establecimiento;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Grupo de manejo: animales que se manejan juntos (ej: "Recría 2026", "Vacas potrero 3"). */
@Entity
@Table(name = "lote")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lote extends Agrupacion {

    public Lote(Establecimiento establecimiento, String nombre) {
        super(establecimiento, nombre);
    }
}
