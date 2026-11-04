package com.innovax.ganadera.invitacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtra solo por organización (@TenantId). El acceso por token, sin usuario, está en InvitacionPublicaRepository. */
public interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {

    @Query("""
        select i from Invitacion i join fetch i.invitadoPor
        where i.establecimiento.id = :establecimientoId
        order by i.createdAt desc
        """)
    List<Invitacion> findDelEstablecimiento(@Param("establecimientoId") UUID establecimientoId);

    Optional<Invitacion> findByIdAndEstablecimientoId(UUID id, UUID establecimientoId);

    /** El email se guarda siempre en minúscula. */
    List<Invitacion> findByEstablecimientoIdAndEmail(UUID establecimientoId, String email);

    /**
     * Emails con una invitación pendiente en algún establecimiento activo de la organización, sin
     * los que ya son usuarios: cada uno va a ocupar un lugar de usuario del plan (#35).
     */
    @Query("""
        select distinct i.email from Invitacion i
        where i.aceptadaAt is null and i.revocadaAt is null and i.venceAt > :ahora
          and i.establecimiento.activo = true
          and i.email not in (select lower(u.email) from Usuario u where u.organizacion.id = :organizacionId)
        """)
    List<String> emailsPendientes(@Param("ahora") Instant ahora, @Param("organizacionId") UUID organizacionId);
}
