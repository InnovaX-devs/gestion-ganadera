package com.innovax.ganadera.evento.tipos;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.ConflictException;

/** Lo común a la venta y la baja (#23): sacar a un animal del stock y, al anular el evento, devolverlo. */
final class SalidaDelStock {

    private SalidaDelStock() {
    }

    /** Pasa el animal a {@code estado}. Los animales que ya no están activos no llegan hasta acá (lo corta EventoService). */
    static void sacar(Animal animal, EstadoAnimal estado) {
        animal.setEstado(estado);
    }

    /**
     * Devuelve al animal al stock. Solo si sigue en el estado que dejó el evento, y si su caravana no
     * la tomó mientras tanto otro animal activo del establecimiento (la caravana es única entre activos).
     */
    static void devolver(Animal animal, EstadoAnimal esperado, String accion, AnimalRepository animalRepository) {
        if (animal.getEstado() != esperado) {
            throw new BusinessException("No se puede anular " + accion + ": " + animal.getCaravana()
                + " ya no figura como " + esperado.name().toLowerCase());
        }
        if (animalRepository.existsByEstablecimientoIdAndCaravanaAndEstadoAndIdNot(
            animal.getEstablecimiento().getId(), animal.getCaravana(), EstadoAnimal.ACTIVO, animal.getId())) {
            throw new ConflictException("No se puede anular " + accion + ": ya hay otro animal activo con la caravana "
                + animal.getCaravana());
        }
        animal.setEstado(EstadoAnimal.ACTIVO);
    }
}
