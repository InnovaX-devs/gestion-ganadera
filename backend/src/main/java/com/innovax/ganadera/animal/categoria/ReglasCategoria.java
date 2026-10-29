package com.innovax.ganadera.animal.categoria;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.innovax.ganadera.animal.Categoria;

/**
 * Reglas de cambio automático de categoría (issue #16). No toca la base: dado cómo está el animal y
 * qué le pasó, dice a qué categoría pasa (si pasa a alguna).
 *
 * <ul>
 *   <li>Ternera → vaquillona: al destete o por edad.</li>
 *   <li>Vaquillona → vaca: al registrar un parto (el primero, porque después ya es vaca).</li>
 *   <li>Ternero → novillito si está castrado, torito si está entero: al destete o por edad.</li>
 *   <li>Novillito → novillo y torito → toro: por edad o por peso.</li>
 * </ul>
 *
 * <p>Evalúa un solo paso. Si el animal está atrasado varias categorías (un ternero de 3 años), el
 * servicio la vuelve a llamar con el resultado hasta que no haya más cambios.
 */
public class ReglasCategoria {

    private final ParametrosCategoria parametros;

    public ReglasCategoria(ParametrosCategoria parametros) {
        this.parametros = parametros;
    }

    /** El cambio de un paso: la categoría nueva y por qué. */
    public record Transicion(Categoria categoriaNueva, MotivoCambio motivo) {
    }

    /**
     * @param fechaNacimiento puede ser null: entonces no se aplica ninguna regla por edad
     * @param castrado        solo importa para el ternero
     */
    public Optional<Transicion> siguiente(Categoria actual, boolean castrado, LocalDate fechaNacimiento, Hecho hecho) {
        return switch (hecho) {
            case Hecho.Destete d -> destete(actual, castrado);
            case Hecho.Parto p -> parto(actual);
            case Hecho.Pesaje p -> peso(actual, p);
            case Hecho.PasoDelTiempo t -> edad(actual, castrado, mesesCumplidos(fechaNacimiento, t.fecha()));
        };
    }

    /** Edad mínima a partir de la cual alguna regla por edad puede aplicar. Sirve para filtrar en la base. */
    public int mesesMinimosParaCambiarPorEdad() {
        return parametros.mesesDestete();
    }

    // ---------- Reglas ----------

    private Optional<Transicion> destete(Categoria actual, boolean castrado) {
        return switch (actual) {
            case TERNERA -> cambio(Categoria.VAQUILLONA, MotivoCambio.DESTETE);
            case TERNERO -> cambio(machoDestetado(castrado), MotivoCambio.DESTETE);
            default -> Optional.empty();
        };
    }

    /**
     * Si figura como ternera y pare, la categoría estaba atrasada: también pasa a vaca. En la vaca no
     * cambia nada (no es el primer parto).
     */
    private Optional<Transicion> parto(Categoria actual) {
        return switch (actual) {
            case VAQUILLONA, TERNERA -> cambio(Categoria.VACA, MotivoCambio.PARTO);
            default -> Optional.empty();
        };
    }

    private Optional<Transicion> peso(Categoria actual, Hecho.Pesaje pesaje) {
        return switch (actual) {
            case NOVILLITO -> pesaje.kilos().compareTo(parametros.kilosNovillo()) >= 0
                ? cambio(Categoria.NOVILLO, MotivoCambio.PESO) : Optional.empty();
            case TORITO -> pesaje.kilos().compareTo(parametros.kilosToro()) >= 0
                ? cambio(Categoria.TORO, MotivoCambio.PESO) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private Optional<Transicion> edad(Categoria actual, boolean castrado, Optional<Long> meses) {
        if (meses.isEmpty()) return Optional.empty();
        long edad = meses.get();
        boolean destetado = edad >= parametros.mesesDestete();
        boolean adulto = edad >= parametros.mesesAdulto();
        return switch (actual) {
            case TERNERA -> destetado ? cambio(Categoria.VAQUILLONA, MotivoCambio.EDAD) : Optional.empty();
            case TERNERO -> destetado ? cambio(machoDestetado(castrado), MotivoCambio.EDAD) : Optional.empty();
            case NOVILLITO -> adulto ? cambio(Categoria.NOVILLO, MotivoCambio.EDAD) : Optional.empty();
            case TORITO -> adulto ? cambio(Categoria.TORO, MotivoCambio.EDAD) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static Categoria machoDestetado(boolean castrado) {
        return castrado ? Categoria.NOVILLITO : Categoria.TORITO;
    }

    private static Optional<Long> mesesCumplidos(LocalDate nacimiento, LocalDate fecha) {
        if (nacimiento == null || fecha.isBefore(nacimiento)) return Optional.empty();
        return Optional.of(ChronoUnit.MONTHS.between(nacimiento, fecha));
    }

    private static Optional<Transicion> cambio(Categoria nueva, MotivoCambio motivo) {
        return Optional.of(new Transicion(nueva, motivo));
    }
}
