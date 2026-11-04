package com.innovax.ganadera.admin.dto;

/** Sesión del panel: un solo token (sin refresh). Al vencer se vuelve a pedir la contraseña. */
public record AdminSesionResponse(String accessToken, String email, long expiresIn) {

    public record Admin(String email) {
    }
}
