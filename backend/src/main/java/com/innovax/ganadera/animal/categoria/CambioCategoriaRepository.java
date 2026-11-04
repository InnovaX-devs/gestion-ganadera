package com.innovax.ganadera.animal.categoria;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CambioCategoriaRepository extends JpaRepository<CambioCategoria, UUID> {

    /** Más reciente primero. */
    List<CambioCategoria> findByAnimalIdOrderByFechaDescCreatedAtDesc(UUID animalId);

    /** Cambios de un animal por un motivo en una fecha, en el orden en que se hicieron. */
    List<CambioCategoria> findByAnimalIdAndMotivoAndFechaOrderByCreatedAtAsc(
        UUID animalId, MotivoCambio motivo, LocalDate fecha);
}
