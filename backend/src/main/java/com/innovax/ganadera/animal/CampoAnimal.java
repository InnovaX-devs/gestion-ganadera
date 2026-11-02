package com.innovax.ganadera.animal;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Campos de la ficha del animal que se resuelven por separado en un conflicto de sincronización
 * (#30, last-write-wins por campo). Lote, rodeo, categoría y estado no están: cambian con eventos
 * (movimiento, venta, baja), que no tienen conflictos. Ver docs/sync.md.
 */
public enum CampoAnimal {

    CARAVANA("caravana", Animal::getCaravana),
    CARAVANA_ELECTRONICA("caravanaElectronica", Animal::getCaravanaElectronica),
    RAZA("raza", Animal::getRaza),
    FECHA_NACIMIENTO("fechaNacimiento", Animal::getFechaNacimiento),
    FECHA_NACIMIENTO_ESTIMADA("fechaNacimientoEstimada", Animal::isFechaNacimientoEstimada),
    OBSERVACIONES("observaciones", Animal::getObservaciones);

    /** Nombre en el JSON de la sincronización y en la columna {@code animal.versiones}. */
    private final String nombre;
    private final Function<Animal, Object> valor;

    CampoAnimal(String nombre, Function<Animal, Object> valor) {
        this.nombre = nombre;
        this.valor = valor;
    }

    public String nombre() {
        return nombre;
    }

    public Object valorEn(Animal animal) {
        return valor.apply(animal);
    }

    public boolean cambio(Animal antes, Object valorAnterior) {
        return !Objects.equals(valorAnterior, valorEn(antes));
    }

    public static Optional<CampoAnimal> deNombre(String nombre) {
        return Arrays.stream(values()).filter(c -> c.nombre.equals(nombre)).findFirst();
    }
}
