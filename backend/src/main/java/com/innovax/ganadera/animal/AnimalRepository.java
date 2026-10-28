package com.innovax.ganadera.animal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Filtra solo por organización (@TenantId). */
public interface AnimalRepository extends JpaRepository<Animal, UUID> {

    /** Trae lote, rodeo, madre y padre en la misma consulta (evita una consulta por animal). */
    @EntityGraph(attributePaths = {"lote", "rodeo", "madre", "padre"})
    List<Animal> findByEstablecimientoIdAndEstadoOrderByCaravanaAsc(UUID establecimientoId, EstadoAnimal estado);

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
}
