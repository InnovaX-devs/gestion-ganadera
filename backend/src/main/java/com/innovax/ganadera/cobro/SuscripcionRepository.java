package com.innovax.ganadera.cobro;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, UUID> {

    Optional<Suscripcion> findByMpId(String mpId);

    /** De la más nueva a la más vieja. */
    List<Suscripcion> findByOrganizacionIdAndEstadoInOrderByCreatedAtDesc(UUID organizacionId,
                                                                          Collection<EstadoSuscripcion> estados);

    List<Suscripcion> findByEstadoIn(Collection<EstadoSuscripcion> estados);
}
