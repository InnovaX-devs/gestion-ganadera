package com.innovax.ganadera.mercado.fuente;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.innovax.ganadera.mercado.Producto;

/**
 * Lo que una fuente devolvió para un producto: un valor, o el motivo por el que no se pudo leer.
 *
 * @param fechaDato fecha que informa la fuente (no la de la consulta)
 * @param nota aclaración para mostrar (ej: "Estimativo: sin operaciones")
 */
public record Lectura(Producto producto, BigDecimal valor, LocalDate fechaDato, String nota, String error) {

    public static Lectura ok(Producto producto, BigDecimal valor, LocalDate fechaDato, String nota) {
        return new Lectura(producto, valor, fechaDato, nota, null);
    }

    public static Lectura fallo(Producto producto, String error) {
        return new Lectura(producto, null, null, null, error);
    }

    public boolean esOk() {
        return error == null && valor != null;
    }
}
