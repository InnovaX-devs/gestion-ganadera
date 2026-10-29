package com.innovax.ganadera.evento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtra solo por organización (@TenantId). */
public interface EventoRepository extends JpaRepository<Evento, UUID>, JpaSpecificationExecutor<Evento> {

    @Override
    @EntityGraph(attributePaths = {"animal", "lote", "usuario"})
    Page<Evento> findAll(Specification<Evento> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"animal", "lote", "usuario"})
    Optional<Evento> findByIdAndEstablecimientoIdAndDeletedAtIsNull(UUID id, UUID establecimientoId);

    @EntityGraph(attributePaths = {"animal", "lote", "usuario"})
    List<Evento> findByOperacionIdOrderByCreatedAtAsc(UUID operacionId);

    boolean existsByOperacionId(UUID operacionId);

    boolean existsByAnimalIdAndDeletedAtIsNull(UUID animalId);

    /** Al borrar un animal cargado por error se borran también sus eventos ya anulados. */
    @Modifying
    @Query("delete from Evento e where e.animal.id = :animalId and e.deletedAt is not null")
    void borrarAnuladosDelAnimal(@Param("animalId") UUID animalId);
}
