package com.innovax.ganadera.mercado.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Relación de reposición: precio del kilo de ternero / precio del kilo de novillo. Cuántos kilos de
 * novillo hay que vender para comprar un kilo de ternero; cuanto más alta, más cara la reposición.
 *
 * @param actualizada false si alguno de los dos precios es un valor anterior (la fuente falló)
 * @param valorAnterior la relación con los precios de la foto anterior (falta si no se puede calcular)
 */
public record RelacionReposicionResponse(
    BigDecimal valor,
    BigDecimal ternero,
    BigDecimal novillo,
    LocalDate fechaTernero,
    LocalDate fechaNovillo,
    boolean actualizada,
    BigDecimal valorAnterior
) {
}
