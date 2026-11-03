package com.innovax.ganadera.cuenta.dto;

/** Lo que ve el dueño al abrir el link de activación, antes de elegir la contraseña. */
public record ActivacionResponse(String email, String nombre, String organizacionNombre) {
}
