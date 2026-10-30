package com.innovax.ganadera.mercado;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {

    Optional<Cotizacion> findByFechaAndProducto(LocalDate fecha, Producto producto);

    /** Último valor conocido antes de esa fecha (ACTUAL o ANTERIOR: los dos tienen valor). */
    Optional<Cotizacion> findFirstByProductoAndFechaBeforeAndValorIsNotNullOrderByFechaDesc(
        Producto producto, LocalDate fecha);

    /** La foto más reciente de cada producto. */
    @Query("""
        select c from Cotizacion c
        where c.fecha = (select max(c2.fecha) from Cotizacion c2 where c2.producto = c.producto)
        """)
    List<Cotizacion> buscarUltimas();

    List<Cotizacion> findByFechaBetweenAndProductoInOrderByFechaAsc(
        LocalDate desde, LocalDate hasta, Collection<Producto> productos);
}
