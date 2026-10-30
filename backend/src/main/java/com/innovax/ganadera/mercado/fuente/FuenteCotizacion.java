package com.innovax.ganadera.mercado.fuente;

import java.util.List;
import java.util.Set;

import com.innovax.ganadera.mercado.Producto;

/**
 * Una fuente externa de cotizaciones (#27). Para sumar una, alcanza con un {@code @Component} que
 * implemente esta interfaz: el job las recorre todas.
 *
 * <p>{@link #leer()} puede tirar una excepción (la fuente no responde, cambió el formato...) o
 * devolver {@link Lectura#fallo} para productos puntuales. En los dos casos el job mantiene el último
 * valor conocido de esos productos y guarda el error.
 */
public interface FuenteCotizacion {

    /** Nombre corto para mostrar y guardar (ej: "DolarApi"). */
    String nombre();

    /** Los productos que informa. Los que no vuelvan en {@link #leer()} se toman como fallidos. */
    Set<Producto> productos();

    List<Lectura> leer();
}
