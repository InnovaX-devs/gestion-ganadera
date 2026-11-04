package com.innovax.ganadera.reproduccion;

import java.time.LocalDate;

/**
 * Cuentas de la gestación bovina (issue #19). La web tiene una copia en
 * {@code features/reproduccion/gestacion.ts} para la vista previa: si cambia algo acá, cambiarlo allá.
 */
public final class Gestacion {

    /** Duración promedio de la gestación de una vaca. */
    public static final int DIAS = 283;

    /** Días de un mes promedio (365,25 / 12), para pasar los meses del tacto a días. */
    private static final double DIAS_POR_MES = 30.44;

    private Gestacion() {
    }

    /** Cuándo se habría preñado, según los meses de gestación que estimó el tacto. */
    public static LocalDate concepcionEstimada(LocalDate fechaTacto, int meses) {
        return fechaTacto.minusDays(Math.round(meses * DIAS_POR_MES));
    }

    /** Concepción estimada + 283 días. Ej: preñada de 3 meses el 1/11 → pare alrededor del 11/5. */
    public static LocalDate fechaEstimadaParto(LocalDate fechaTacto, int meses) {
        return concepcionEstimada(fechaTacto, meses).plusDays(DIAS);
    }
}
