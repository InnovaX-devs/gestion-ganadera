package com.innovax.ganadera.cuenta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivacionCuentaRepository extends JpaRepository<ActivacionCuenta, UUID> {

    @EntityGraph(attributePaths = {"usuario", "usuario.organizacion"})
    Optional<ActivacionCuenta> findByTokenHash(String tokenHash);

    /** Si el usuario todavía no activó su cuenta (tiene un link sin usar). */
    boolean existsByUsuarioIdAndUsadaAtIsNull(UUID usuarioId);

    List<ActivacionCuenta> findByUsuarioIdAndUsadaAtIsNull(UUID usuarioId);
}
