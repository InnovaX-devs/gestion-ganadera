-- Issue #12: invitaciones por email a un establecimiento.
-- El link lleva un token al azar; en la base se guarda solo su hash (SHA-256), así que quien vea la
-- tabla no puede armar links válidos.

CREATE TABLE invitacion (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    invitado_por_id    UUID                     NOT NULL REFERENCES usuario (id),
    email              VARCHAR(255)             NOT NULL,
    rol                VARCHAR(20)              NOT NULL,
    token_hash         VARCHAR(64)              NOT NULL,
    vence_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    -- El estado se deduce de estas dos fechas y de vence_at: PENDIENTE, ACEPTADA, REVOCADA o VENCIDA
    aceptada_at        TIMESTAMP WITH TIME ZONE,
    revocada_at        TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invitacion_rol CHECK (rol IN ('DUENO', 'ENCARGADO', 'VETERINARIO', 'SOCIO')),
    CONSTRAINT uq_invitacion_token UNIQUE (token_hash)
);

CREATE INDEX idx_invitacion_organizacion ON invitacion (organizacion_id);
CREATE INDEX idx_invitacion_establecimiento_email ON invitacion (establecimiento_id, email);
