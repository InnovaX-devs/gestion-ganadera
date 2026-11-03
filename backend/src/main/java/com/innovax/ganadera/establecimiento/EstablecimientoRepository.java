package com.innovax.ganadera.establecimiento;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Todas las consultas filtran solas por la organización del usuario logueado (@TenantId). Qué
 * establecimientos ve cada usuario lo resuelven las membresías (ver MembresiaRepository).
 */
public interface EstablecimientoRepository extends JpaRepository<Establecimiento, UUID> {

    Optional<Establecimiento> findByIdAndActivoTrue(UUID id);

    boolean existsByNombreIgnoreCaseAndActivoTrue(String nombre);

    /** Establecimientos activos de la organización (límite del plan, #35). */
    long countByActivoTrue();

    boolean existsByNombreIgnoreCaseAndActivoTrueAndIdNot(String nombre, UUID id);
}
