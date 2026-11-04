package com.innovax.ganadera.reproduccion.dto;

import java.time.LocalDate;
import java.util.List;

import com.innovax.ganadera.evento.tipos.ResultadoTacto;
import com.innovax.ganadera.evento.tipos.TipoServicio;
import com.innovax.ganadera.reproduccion.EstadoReproductivo;

/**
 * Estado reproductivo de una vaca o vaquillona (#19), para la ficha. Los otros animales no lo tienen.
 *
 * @param desde              fecha del evento que define el estado (el último servicio, tacto o parto).
 * @param fechaEstimadaParto solo si está preñada: la del último tacto.
 * @param ultimoServicio     null si nunca se registró uno. Igual para {@code ultimoTacto} y {@code ultimoParto}.
 */
public record EstadoReproductivoResponse(
    EstadoReproductivo estado,
    LocalDate desde,
    LocalDate fechaEstimadaParto,
    Servicio ultimoServicio,
    Tacto ultimoTacto,
    LocalDate ultimoParto
) {

    public static final EstadoReproductivoResponse SIN_DATOS =
        new EstadoReproductivoResponse(EstadoReproductivo.SIN_DATOS, null, null, null, null, null);

    /** @param toros caravanas de los toros, si se indicaron. */
    public record Servicio(LocalDate fecha, LocalDate fechaFin, TipoServicio tipo, List<String> toros,
                           String pajuela) {
    }

    public record Tacto(LocalDate fecha, ResultadoTacto resultado, Integer mesesGestacion, String veterinario,
                        LocalDate fechaEstimadaParto) {
    }
}
