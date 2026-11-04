package com.innovax.ganadera.auth.dto;

import java.util.UUID;

import com.innovax.ganadera.usuario.Usuario;

public record UsuarioResponse(
    UUID id,
    String email,
    String nombre,
    String apellido,
    UUID organizacionId,
    String organizacionNombre
) {

    /** Requiere una transacción abierta: lee la organización (relación lazy). */
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
            usuario.getId(),
            usuario.getEmail(),
            usuario.getNombre(),
            usuario.getApellido(),
            usuario.getOrganizacion().getId(),
            usuario.getOrganizacion().getNombre());
    }
}
