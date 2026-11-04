-- "Olvidé mi contraseña": link de un solo uso, de vida corta. Como las invitaciones, solo se guarda el hash del token.
CREATE TABLE restablecimiento_contrasena (
    id          UUID                     PRIMARY KEY,
    usuario_id  UUID                     NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64)              NOT NULL UNIQUE,
    vence_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_at    TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_restablecimiento_contrasena_usuario ON restablecimiento_contrasena (usuario_id);

-- Al cambiar la contraseña, las sesiones abiertas (refresh tokens anteriores) dejan de servir.
ALTER TABLE usuario ADD COLUMN credenciales_cambiadas_at TIMESTAMP WITH TIME ZONE;
