package com.innovax.ganadera.agrupacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Consultas comunes a lotes y rodeos. Filtran solas por organización (@TenantId). */
@NoRepositoryBean
public interface AgrupacionRepository<E extends Agrupacion> extends JpaRepository<E, UUID> {

    List<E> findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(UUID establecimientoId);

    Optional<E> findByIdAndEstablecimientoIdAndActivoTrue(UUID id, UUID establecimientoId);

    boolean existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrue(UUID establecimientoId, String nombre);

    boolean existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(
        UUID establecimientoId, String nombre, UUID id);

    long countByEstablecimientoIdAndActivoTrue(UUID establecimientoId);
}
