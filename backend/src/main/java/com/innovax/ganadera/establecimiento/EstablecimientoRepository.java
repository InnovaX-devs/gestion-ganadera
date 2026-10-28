package com.innovax.ganadera.establecimiento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Todas las consultas filtran solas por la organización del usuario logueado (@TenantId). */
public interface EstablecimientoRepository extends JpaRepository<Establecimiento, UUID> {

    List<Establecimiento> findByActivoTrueOrderByNombreAsc();

    Optional<Establecimiento> findByIdAndActivoTrue(UUID id);

    boolean existsByNombreIgnoreCaseAndActivoTrue(String nombre);

    boolean existsByNombreIgnoreCaseAndActivoTrueAndIdNot(String nombre, UUID id);
}
