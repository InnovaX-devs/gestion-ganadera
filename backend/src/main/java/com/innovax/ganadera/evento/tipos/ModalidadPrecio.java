package com.innovax.ganadera.evento.tipos;

/** Cómo se pacta el precio de una venta (#23). */
public enum ModalidadPrecio {
    /** El precio es por kilo vivo: el importe es precio × peso. */
    POR_KG,
    /** El precio es por animal, sin importar cuánto pese. */
    POR_CABEZA
}
