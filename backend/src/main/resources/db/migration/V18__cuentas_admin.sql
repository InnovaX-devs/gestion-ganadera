-- Issue #35: el equipo de Innovax crea y suspende cuentas desde el panel de administración.

-- Una cuenta suspendida conserva sus datos, pero nadie de ella puede entrar.
ALTER TABLE organizacion ADD COLUMN suspendida BOOLEAN DEFAULT FALSE NOT NULL;

-- Link para que el dueño de una cuenta creada desde el panel ponga su contraseña.
-- Como las invitaciones, solo se guarda el hash del token.
CREATE TABLE activacion_cuenta (
    id          UUID                     PRIMARY KEY,
    usuario_id  UUID                     NOT NULL REFERENCES usuario (id),
    token_hash  VARCHAR(64)              NOT NULL UNIQUE,
    vence_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    usada_at    TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activacion_cuenta_usuario ON activacion_cuenta (usuario_id);
