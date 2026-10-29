package com.innovax.ganadera.animal.categoria;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Umbrales de las reglas de cambio de categoría: {@code app.categorias.*} en application.yml.
 *
 * <p>Por ahora son iguales para toda la instalación. Cuando se puedan configurar por organización o
 * establecimiento ("configurables más adelante", issue #16), esto pasa a ser el valor por defecto.
 *
 * @param mesesDestete edad a la que un ternero/a deja de serlo aunque no se haya registrado el destete
 * @param mesesAdulto  edad a la que el novillito pasa a novillo y el torito a toro
 * @param kilosNovillo peso a partir del cual el novillito pasa a novillo
 * @param kilosToro    peso a partir del cual el torito pasa a toro
 */
@ConfigurationProperties(prefix = "app.categorias")
public record ParametrosCategoria(
    Integer mesesDestete,
    Integer mesesAdulto,
    BigDecimal kilosNovillo,
    BigDecimal kilosToro
) {

    public static final ParametrosCategoria POR_DEFECTO = new ParametrosCategoria(null, null, null, null);

    public ParametrosCategoria {
        if (mesesDestete == null) mesesDestete = 8;
        if (mesesAdulto == null) mesesAdulto = 24;
        if (kilosNovillo == null) kilosNovillo = new BigDecimal("400");
        if (kilosToro == null) kilosToro = new BigDecimal("450");
        if (mesesDestete <= 0 || mesesAdulto <= mesesDestete) {
            throw new IllegalStateException(
                "app.categorias: meses-destete tiene que ser mayor a 0 y menor que meses-adulto");
        }
        if (kilosNovillo.signum() <= 0 || kilosToro.signum() <= 0) {
            throw new IllegalStateException("app.categorias: los pesos tienen que ser mayores a 0");
        }
    }
}
