package com.innovax.ganadera.contrasena;

import java.time.Instant;

/** Lleva el token en claro: es el único momento en que existe. El email sale después del commit. */
record RestablecimientoEnviadoEvent(String destinatario, String nombre, String token, Instant venceAt,
                                    boolean pedidoPorInnovax) {
}
