package com.innovax.ganadera.evento.tipos;

import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.common.error.BusinessException;

/** Reglas compartidas por servicio y tacto (#19). */
final class Reproduccion {

    private Reproduccion() {
    }

    /**
     * Solo vacas y vaquillonas se sirven y se tactan. Al cargar por rodeo, el resto del rodeo
     * (toros, terneros al pie) queda afuera sin error.
     */
    static boolean esHembraDeCria(Animal animal) {
        return animal.getCategoria() == Categoria.VACA || animal.getCategoria() == Categoria.VAQUILLONA;
    }

    /** Elegidas una por una, una que no corresponde es un error de carga: se avisa cuál. */
    static void exigirHembrasDeCria(Iterable<Animal> animales, String evento) {
        for (Animal animal : animales) {
            if (!esHembraDeCria(animal)) {
                throw new BusinessException(animal.getCaravana() + " no es vaca ni vaquillona: el " + evento
                    + " se registra solo en hembras de cría");
            }
        }
    }

    static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
