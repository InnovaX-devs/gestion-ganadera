package com.innovax.ganadera.auth.dto;

import com.innovax.ganadera.auth.TokenService;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UsuarioResponse usuario
) {

    public static AuthResponse of(TokenService.Tokens tokens, UsuarioResponse usuario) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
            tokens.expiresInSeconds(), usuario);
    }
}
