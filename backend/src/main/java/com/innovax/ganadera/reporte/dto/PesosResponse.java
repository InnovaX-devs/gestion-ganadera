package com.innovax.ganadera.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;

/**
 * Reporte de evolución de pesos y ganancia diaria (#32). Solo cuentan los pesajes del período: la
 * ganancia de cada animal es (último peso − primer peso) / días entre esas dos pesadas. Un animal
 * con una sola pesada en el período no tiene ganancia.
 *
 * <p>Lote y categoría son los actuales del animal (no los que tenía al pesarse).
 */
public record PesosResponse(
    LocalDate desde,
    LocalDate hasta,
    Resumen resumen,
    List<PorFecha> porFecha,
    List<PorCategoria> porCategoria,
    List<PorAnimal> porAnimal
) {

    /**
     * @param gananciaDiariaPromedio promedio de las ganancias de los animales que la tienen (kg/día)
     */
    public record Resumen(
        int animales,
        int pesajes,
        int animalesConGanancia,
        BigDecimal pesoInicialPromedio,
        BigDecimal pesoFinalPromedio,
        BigDecimal gananciaDiariaPromedio
    ) {
    }

    /** Cómo evoluciona el peso promedio del rodeo: una fila por fecha con pesajes. */
    public record PorFecha(LocalDate fecha, int pesajes, BigDecimal kgPromedio) {
    }

    public record PorCategoria(Categoria categoria, int animales, BigDecimal pesoFinalPromedio,
                               BigDecimal gananciaDiariaPromedio) {
    }

    public record PorAnimal(
        UUID animalId,
        String caravana,
        Categoria categoria,
        String lote,
        int pesajes,
        LocalDate fechaInicial,
        BigDecimal pesoInicial,
        LocalDate fechaFinal,
        BigDecimal pesoFinal,
        Integer dias,
        BigDecimal variacionKg,
        BigDecimal gananciaDiaria
    ) {
    }
}
