package com.innovax.ganadera.reporte;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.evento.Evento;
import com.innovax.ganadera.evento.TipoEvento;

/** Consultas de los reportes (#32). JPQL: Hibernate agrega el filtro por organización (@TenantId). */
public interface ReporteRepository extends Repository<Evento, UUID> {

    /** Pesajes vigentes del establecimiento en el período, con su animal, ordenados por animal y fecha. */
    @Query("""
        select e from Evento e
        join fetch e.animal a
        left join fetch a.lote
        where e.establecimiento.id = :establecimientoId and e.tipo = :tipo and e.deletedAt is null
          and e.fecha between :desde and :hasta
        order by a.id, e.fecha, e.createdAt
        """)
    List<Evento> pesajes(@Param("establecimientoId") UUID establecimientoId, @Param("tipo") TipoEvento tipo,
                         @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Animales activos del establecimiento, con lote y rodeo. */
    @Query("""
        select a from Animal a
        left join fetch a.lote
        left join fetch a.rodeo
        where a.establecimiento.id = :establecimientoId and a.estado = :estado
        """)
    List<Animal> animales(@Param("establecimientoId") UUID establecimientoId, @Param("estado") EstadoAnimal estado);
}
