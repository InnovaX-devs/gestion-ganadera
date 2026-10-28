package com.innovax.ganadera.agrupacion;

import com.innovax.ganadera.establecimiento.Establecimiento;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Grupo productivo (ej: "Rodeo de cría", "Vaquillonas de reposición"). */
@Entity
@Table(name = "rodeo")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rodeo extends Agrupacion {

    public Rodeo(Establecimiento establecimiento, String nombre) {
        super(establecimiento, nombre);
    }
}
