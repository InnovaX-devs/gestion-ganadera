package com.innovax.ganadera.animal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtra solo por organización (@TenantId), también en las consultas con Specification. */
public interface AnimalRepository extends JpaRepository<Animal, UUID>, JpaSpecificationExecutor<Animal> {

    /** Listado con filtros (ver AnimalSpecs). Trae lote, rodeo, madre y padre en la misma consulta. */
    @Override
    @EntityGraph(attributePaths = {"lote", "rodeo", "madre", "padre"})
    Page<Animal> findAll(Specification<Animal> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"lote", "rodeo", "madre", "padre"})
    Optional<Animal> findByIdAndEstablecimientoId(UUID id, UUID establecimientoId);

    boolean existsByEstablecimientoIdAndCaravanaAndEstado(UUID establecimientoId, String caravana, EstadoAnimal estado);

    boolean existsByEstablecimientoIdAndCaravanaAndEstadoAndIdNot(
        UUID establecimientoId, String caravana, EstadoAnimal estado, UUID id);

    /** La caravana electrónica es única en toda la organización: identifica al animal aunque cambie de campo. */
    boolean existsByCaravanaElectronicaAndEstado(String caravanaElectronica, EstadoAnimal estado);

    boolean existsByCaravanaElectronicaAndEstadoAndIdNot(String caravanaElectronica, EstadoAnimal estado, UUID id);

    long countByLoteIdAndEstado(UUID loteId, EstadoAnimal estado);

    long countByRodeoIdAndEstado(UUID rodeoId, EstadoAnimal estado);

    long countByEstablecimientoIdAndEstado(UUID establecimientoId, EstadoAnimal estado);

    /** Animales activos de toda la organización. Es la medida de los planes (#35). */
    long countByEstado(EstadoAnimal estado);

    boolean existsByMadreIdOrPadreId(UUID madreId, UUID padreId);

    /** Cantidad por categoría de los animales en un estado. Cada fila: [Categoria, Long]. */
    @Query("""
        select a.categoria, count(a) from Animal a
        where a.establecimiento.id = :establecimientoId and a.estado = :estado
        group by a.categoria
        """)
    List<Object[]> contarPorCategoria(@Param("establecimientoId") UUID establecimientoId,
                                      @Param("estado") EstadoAnimal estado);
}
