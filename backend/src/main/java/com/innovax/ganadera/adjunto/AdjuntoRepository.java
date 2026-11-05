package com.innovax.ganadera.adjunto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Filtra solo por organización (@TenantId). */
public interface AdjuntoRepository extends JpaRepository<Adjunto, UUID> {

    Optional<Adjunto> findByCargaId(UUID cargaId);

    List<Adjunto> findByOperacionIdInOrderByCreatedAtAsc(Collection<UUID> operacionIds);

    List<Adjunto> findByOperacionId(UUID operacionId);

    List<Adjunto> findByAnimalIdOrderByCreatedAtDesc(UUID animalId);

    List<Adjunto> findByAnimalId(UUID animalId);

    long countByOperacionId(UUID operacionId);

    long countByAnimalId(UUID animalId);
}
