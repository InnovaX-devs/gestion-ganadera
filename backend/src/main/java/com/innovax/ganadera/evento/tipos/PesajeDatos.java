package com.innovax.ganadera.evento.tipos;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Issue #21. Un pesaje, de dos formas:
 *
 * <ul>
 *   <li><b>Individual:</b> {@code kg} del animal.</li>
 *   <li><b>Grupal:</b> {@code pesoTotal} de un grupo y {@code cantidad} de animales pesados. El
 *       servidor calcula {@code kg} = promedio y se lo asigna a cada animal del grupo, que queda con
 *       su pesaje (marcado {@code grupal}).</li>
 * </ul>
 *
 * Lo que se guarda siempre tiene {@code kg}: así la ficha, el gráfico y la ganancia de peso leen lo
 * mismo sin importar cómo se pesó. {@code grupal}, {@code pesoTotal} y {@code cantidad} los completa
 * el servidor.
 */
public record PesajeDatos(
    @Positive(message = "El peso tiene que ser mayor a 0")
    @DecimalMax(value = "1500", message = "El peso máximo es 1500 kg")
    @Digits(integer = 4, fraction = 2, message = "Hasta 2 decimales")
    BigDecimal kg,

    @Positive(message = "El peso total tiene que ser mayor a 0")
    @DecimalMax(value = "1000000", message = "El peso total máximo es 1.000.000 kg")
    @Digits(integer = 7, fraction = 2, message = "Hasta 2 decimales")
    BigDecimal pesoTotal,

    @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
    @Max(value = 10000, message = "La cantidad máxima es 10.000")
    Integer cantidad,

    Boolean grupal
) {

    public boolean esGrupal() {
        return Boolean.TRUE.equals(grupal);
    }
}
