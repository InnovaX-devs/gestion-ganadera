-- Issue #9: establecimientos (campos) de cada organización.
CREATE TABLE establecimiento (
    id               UUID                     PRIMARY KEY,
    organizacion_id  UUID                     NOT NULL REFERENCES organizacion (id),
    nombre           VARCHAR(150)             NOT NULL,
    localidad        VARCHAR(100),
    provincia        VARCHAR(100),
    superficie_ha    NUMERIC(12, 2),
    renspa           VARCHAR(30),
    latitud          NUMERIC(9, 6),
    longitud         NUMERIC(9, 6),
    -- Baja lógica: los establecimientos no se borran porque van a tener historial (animales, eventos)
    activo           BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_establecimiento_organizacion ON establecimiento (organizacion_id);
