package com.innovax.ganadera.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Usuario no es de un tenant: la organización va explícita (límite del plan, #35). */
    long countByOrganizacionIdAndActivoTrue(UUID organizacionId);

    /** Quien creó la cuenta (el primer usuario). Para el panel de administración. */
    Optional<Usuario> findFirstByOrganizacionIdOrderByCreatedAtAsc(UUID organizacionId);
}
