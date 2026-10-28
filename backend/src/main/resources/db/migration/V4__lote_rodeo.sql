-- Issue #10: lotes y rodeos, las dos formas de agrupar animales dentro de un establecimiento.
-- Un animal va a estar en un lote (manejo: dónde y cómo se maneja) y en un rodeo (grupo productivo).

CREATE TABLE lote (
    id                UUID                     PRIMARY KEY,
    organizacion_id   UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                    NOT NULL REFERENCES establecimiento (id),
    nombre            VARCHAR(100)             NOT NULL,
    descripcion       VARCHAR(500),
    activo            BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_lote_organizacion ON lote (organizacion_id);
CREATE INDEX idx_lote_establecimiento ON lote (establecimiento_id);

CREATE TABLE rodeo (
    id                UUID                     PRIMARY KEY,
    organizacion_id   UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                    NOT NULL REFERENCES establecimiento (id),
    nombre            VARCHAR(100)             NOT NULL,
    descripcion       VARCHAR(500),
    activo            BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rodeo_organizacion ON rodeo (organizacion_id);
CREATE INDEX idx_rodeo_establecimiento ON rodeo (establecimiento_id);
