package com.innovax.ganadera.membresia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Filtra solo por organización (@TenantId), como el resto de los repositorios. */
public interface MembresiaRepository extends JpaRepository<Membresia, UUID> {

    Optional<Membresia> findByUsuarioIdAndEstablecimientoId(UUID usuarioId, UUID establecimientoId);

    Optional<Membresia> findByIdAndEstablecimientoId(UUID id, UUID establecimientoId);

    boolean existsByUsuarioIdAndEstablecimientoId(UUID usuarioId, UUID establecimientoId);

    long countByEstablecimientoIdAndRol(UUID establecimientoId, Rol rol);

    /** Establecimientos activos a los que el usuario tiene acceso, con su rol en cada uno. */
    @Query("""
        select m from Membresia m join fetch m.establecimiento e
        where m.usuario.id = :usuarioId and e.activo = true
        order by e.nombre
        """)
    List<Membresia> findDelUsuario(@Param("usuarioId") UUID usuarioId);

    @Query("""
        select m from Membresia m join fetch m.usuario u
        where m.establecimiento.id = :establecimientoId
        order by u.nombre, u.apellido
        """)
    List<Membresia> findDelEstablecimiento(@Param("establecimientoId") UUID establecimientoId);
}
