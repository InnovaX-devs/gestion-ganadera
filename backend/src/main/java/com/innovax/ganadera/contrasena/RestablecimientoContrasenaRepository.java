package com.innovax.ganadera.contrasena;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestablecimientoContrasenaRepository extends JpaRepository<RestablecimientoContrasena, UUID> {

    @EntityGraph(attributePaths = {"usuario", "usuario.organizacion"})
    Optional<RestablecimientoContrasena> findByTokenHash(String tokenHash);

    List<RestablecimientoContrasena> findByUsuarioIdAndUsadoAtIsNull(UUID usuarioId);

    Optional<RestablecimientoContrasena> findFirstByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId);
}
