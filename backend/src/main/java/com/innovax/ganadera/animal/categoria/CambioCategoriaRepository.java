package com.innovax.ganadera.animal.categoria;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CambioCategoriaRepository extends JpaRepository<CambioCategoria, UUID> {

    /** Más reciente primero. */
    List<CambioCategoria> findByAnimalIdOrderByFechaDescCreatedAtDesc(UUID animalId);
}
