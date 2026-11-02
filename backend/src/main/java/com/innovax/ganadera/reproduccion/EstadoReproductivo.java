package com.innovax.ganadera.reproduccion;

/**
 * En qué está una vaca o vaquillona (#19). Sale del último servicio, tacto o parto vigente (ver
 * {@link EstadoReproductivoService}); no se guarda en el animal.
 */
public enum EstadoReproductivo {
    /** Sin servicios, tactos ni partos registrados. */
    SIN_DATOS,
    /** Servicio natural en curso: sin fecha de fin, o con la fecha de fin por delante. */
    EN_SERVICIO,
    /** Servicio terminado (o inseminada) y todavía sin tacto. */
    SERVIDA,
    PRENADA,
    VACIA,
    /** Lo último fue un parto: está criando y todavía no se volvió a servir. */
    PARIDA
}
