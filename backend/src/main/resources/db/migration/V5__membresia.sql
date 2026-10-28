-- Issue #11: membresías. Un usuario tiene un rol distinto en cada establecimiento.
-- Los roles son: DUENO, ENCARGADO, VETERINARIO y SOCIO (ver docs/permisos.md).

CREATE TABLE membresia (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    usuario_id         UUID                     NOT NULL REFERENCES usuario (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    rol                VARCHAR(20)              NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_membresia_rol CHECK (rol IN ('DUENO', 'ENCARGADO', 'VETERINARIO', 'SOCIO')),
    -- Un usuario tiene un solo rol por establecimiento
    CONSTRAINT uq_membresia_usuario_establecimiento UNIQUE (usuario_id, establecimiento_id)
);

CREATE INDEX idx_membresia_organizacion ON membresia (organizacion_id);
CREATE INDEX idx_membresia_establecimiento ON membresia (establecimiento_id);

-- Hasta ahora cualquier usuario de una organización veía todos sus establecimientos.
-- Para no dejar a nadie sin acceso, los usuarios existentes pasan a ser DUENO de los establecimientos
-- de su organización.
-- El id se arma con la primera mitad del id del usuario y la segunda del establecimiento: así no
-- hace falta una función para generar UUIDs (que cambia entre PostgreSQL y H2) y no se repite.
INSERT INTO membresia (id, organizacion_id, usuario_id, establecimiento_id, rol)
SELECT CAST(SUBSTRING(CAST(u.id AS VARCHAR(36)), 1, 18) || SUBSTRING(CAST(e.id AS VARCHAR(36)), 19) AS UUID),
       e.organizacion_id, u.id, e.id, 'DUENO'
FROM establecimiento e
JOIN usuario u ON u.organizacion_id = e.organizacion_id;
