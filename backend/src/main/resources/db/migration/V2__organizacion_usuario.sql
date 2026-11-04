-- Issue #8: modelo multi-tenant.
-- Una organización es una cuenta (un productor o empresa). Todo dato de negocio pertenece a una.

CREATE TABLE organizacion (
    id          UUID                     PRIMARY KEY,
    nombre      VARCHAR(150)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- El email es único global: el login busca al usuario antes de conocer su organización.
CREATE TABLE usuario (
    id               UUID                     PRIMARY KEY,
    organizacion_id  UUID                     NOT NULL REFERENCES organizacion (id),
    email            VARCHAR(255)             NOT NULL UNIQUE,
    password_hash    VARCHAR(255)             NOT NULL,
    nombre           VARCHAR(100)             NOT NULL,
    apellido         VARCHAR(100),
    activo           BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuario_organizacion ON usuario (organizacion_id);
