package com.innovax.ganadera.evento.tipos;

import com.innovax.ganadera.animal.EstadoAnimal;

/** Por qué un animal sale del stock sin venderse (#23). El motivo decide el estado en que queda. */
public enum MotivoBaja {
    MUERTE(EstadoAnimal.MUERTO),
    PERDIDA(EstadoAnimal.PERDIDO);

    private final EstadoAnimal estado;

    MotivoBaja(EstadoAnimal estado) {
        this.estado = estado;
    }

    public EstadoAnimal estado() {
        return estado;
    }
}
