package com.innovax.ganadera.stock;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.EstadoAnimal;

/**
 * Consulta del stock. Es JPQL (no nativa ni una vista de la base) para que Hibernate agregue solo
 * el filtro por organización (@TenantId).
 *
 * <p>Una sola consulta agrupada por las cuatro dimensiones; los totales por categoría, sexo, lote y
 * rodeo se suman en memoria a partir de estas filas (son pocas: categorías × lotes × rodeos en uso).
 * {@code a.lote.id} y {@code a.rodeo.id} se leen de la foreign key, sin join. Usa el índice
 * {@code idx_animal_stock} (V10).
 */
public interface StockRepository extends Repository<Animal, UUID> {

    @Query("""
        select new com.innovax.ganadera.stock.StockFila(a.categoria, a.sexo, a.lote.id, a.rodeo.id, count(a))
        from Animal a
        where a.establecimiento.id = :establecimientoId and a.estado = :estado
        group by a.categoria, a.sexo, a.lote.id, a.rodeo.id
        """)
    List<StockFila> contar(@Param("establecimientoId") UUID establecimientoId,
                           @Param("estado") EstadoAnimal estado);
}
