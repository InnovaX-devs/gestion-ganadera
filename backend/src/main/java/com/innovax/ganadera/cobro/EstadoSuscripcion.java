package com.innovax.ganadera.cobro;

/** Estado de una suscripción en nuestro sistema (#35). */
public enum EstadoSuscripcion {
    /** Creada, esperando que el cliente pague en Mercado Pago. */
    PENDIENTE,
    /** Autorizada en Mercado Pago: se cobra todos los meses. */
    ACTIVA,
    /** Pausada desde Mercado Pago: no se cobra, pero el plan sigue mientras dure lo pago + la gracia. */
    PAUSADA,
    /** Cancelada por el cliente: el plan sigue hasta el último día pago (sin gracia). */
    CANCELADA,
    /** Se terminó lo pago (y la gracia): la cuenta pasó al plan Inicial. */
    VENCIDA,
    /** El cliente se pasó a otro plan con una suscripción nueva. */
    REEMPLAZADA;

    /** Mapea el {@code status} de un preapproval de Mercado Pago. */
    public static EstadoSuscripcion deMercadoPago(String status) {
        if (status == null) return PENDIENTE;
        return switch (status) {
            case "authorized" -> ACTIVA;
            case "paused" -> PAUSADA;
            case "cancelled" -> CANCELADA;
            default -> PENDIENTE;
        };
    }
}
