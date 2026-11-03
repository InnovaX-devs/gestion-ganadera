package com.innovax.ganadera.cuenta;

import java.time.Instant;

/** Lleva el token en claro: es el único momento en que existe. El email sale después del commit. */
record ActivacionEnviadaEvent(String destinatario, String nombre, String organizacion, String token,
                              Instant venceAt) {
}
