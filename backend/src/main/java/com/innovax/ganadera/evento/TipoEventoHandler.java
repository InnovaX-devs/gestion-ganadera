package com.innovax.ganadera.evento;

import java.time.LocalDate;
import java.util.List;

import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.establecimiento.Establecimiento;

/**
 * Lo propio de cada tipo de evento. <b>Para agregar un tipo</b> (issues #18 a #23):
 *
 * <ol>
 *   <li>Crear un record con sus datos y validaciones (ej: {@code PesajeDatos(@Positive BigDecimal kg)}).</li>
 *   <li>Crear un {@code @Component} que implemente esta interfaz. Se registra solo.</li>
 *   <li>Si el evento cambia algo del animal (estado, lote, categoría), hacerlo en {@link #aplicar}
 *       y deshacerlo en {@link #revertir}.</li>
 * </ol>
 *
 * Ver {@code tipos.ObservacionHandler} como ejemplo (y {@code tipos.TratamientoHandler} para un tipo
 * que completa sus datos desde otra tabla).
 *
 * @param <D> record con los datos del tipo. Se valida con Bean Validation y se guarda como JSON.
 */
public interface TipoEventoHandler<D> {

    TipoEvento tipo();

    Class<D> claseDatos();

    /**
     * Reglas propias del tipo, además de las comunes (animales del establecimiento, fecha no futura
     * ni anterior al nacimiento). Lanzar {@code BusinessException} si algo no corresponde.
     */
    default void validar(D datos, Contexto contexto) {
    }

    /**
     * Completa los datos con lo que resuelve el servidor (ej: el nombre del producto a partir de su
     * id) antes de guardarlos. Se llama después de {@link #validar}. Puede lanzar {@code BusinessException}.
     */
    default D completar(D datos, Contexto contexto) {
        return datos;
    }

    /**
     * Completa los datos con lo que es propio de cada animal (ej: de dónde sale en un movimiento).
     * Se llama una vez por animal, después de {@link #completar}, y el resultado es lo que se guarda
     * en el evento de ese animal y lo que recibe {@link #aplicar}. Puede lanzar {@code BusinessException}.
     */
    default D completarPorAnimal(D datos, Animal animal, Contexto contexto) {
        return datos;
    }

    /** Efecto sobre el animal (si lo hay). Se llama una vez por cada evento creado. */
    default void aplicar(Evento evento, D datos) {
    }

    /** Deshace lo que hizo {@link #aplicar} cuando el evento se anula. */
    default void revertir(Evento evento, D datos) {
    }

    /** Lo que se está registrando, para que el handler pueda validar con todo a la vista. */
    record Contexto(Establecimiento establecimiento, LocalDate fecha, List<Animal> animales, Lote lote) {
    }
}
