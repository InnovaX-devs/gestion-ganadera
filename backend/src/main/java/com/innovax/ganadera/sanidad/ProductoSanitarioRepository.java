package com.innovax.ganadera.sanidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Filtra solo por organización (@TenantId). */
public interface ProductoSanitarioRepository extends JpaRepository<ProductoSanitario, UUID> {

    List<ProductoSanitario> findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(UUID establecimientoId);

    Optional<ProductoSanitario> findByIdAndEstablecimientoIdAndActivoTrue(UUID id, UUID establecimientoId);

    boolean existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrue(UUID establecimientoId, String nombre);

    boolean existsByEstablecimientoIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(
        UUID establecimientoId, String nombre, UUID id);
}
