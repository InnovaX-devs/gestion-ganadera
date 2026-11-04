package com.innovax.ganadera.calendario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.EstadoAnimal;

public interface PlanSanitarioRepository extends JpaRepository<TratamientoPlanificado, UUID> {

    Optional<TratamientoPlanificado> findByIdAndEstablecimientoId(UUID id, UUID establecimientoId);

    /** Del período, con lo que hace falta para mostrarlos (sin consultas extra). */
    @Query("""
        select p from TratamientoPlanificado p
        left join fetch p.lote left join fetch p.rodeo left join fetch p.producto left join fetch p.creadoPor
        where p.establecimiento.id = :establecimientoId and p.fecha between :desde and :hasta
        order by p.fecha, p.createdAt
        """)
    List<TratamientoPlanificado> delPeriodo(@Param("establecimientoId") UUID establecimientoId,
                                            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Pendientes hasta esa fecha (incluye los vencidos de cualquier fecha anterior). */
    @Query("""
        select p from TratamientoPlanificado p
        left join fetch p.lote left join fetch p.rodeo left join fetch p.producto left join fetch p.creadoPor
        where p.establecimiento.id = :establecimientoId and p.estado = :estado and p.fecha <= :hasta
        order by p.fecha, p.createdAt
        """)
    List<TratamientoPlanificado> pendientesHasta(@Param("establecimientoId") UUID establecimientoId,
                                                 @Param("estado") EstadoPlan estado,
                                                 @Param("hasta") LocalDate hasta);

    /** Animales activos del establecimiento (para las carencias). */
    @Query("select a from Animal a where a.establecimiento.id = :establecimientoId and a.estado = :estado")
    List<Animal> animalesActivos(@Param("establecimientoId") UUID establecimientoId,
                                 @Param("estado") EstadoAnimal estado);
}
