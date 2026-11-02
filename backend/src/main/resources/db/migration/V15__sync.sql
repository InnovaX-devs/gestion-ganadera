-- Issue #30: sincronización con la app offline. Ver docs/sync.md.

-- Última modificación de cada campo editable de la ficha del animal, para resolver conflictos
-- campo por campo (last-write-wins). Formato: "caravana=1761832800000;raza=..." (epoch millis).
-- Null o sin un campo = nunca se editó desde que existe esto (vale created_at).
ALTER TABLE animal ADD COLUMN versiones VARCHAR(1000);

-- Bajas para la sincronización: animales que salieron de un establecimiento (borrados o trasladados
-- a otro). El celular no puede enterarse de otra forma: la fila ya no está, o es de otro campo.
CREATE TABLE sync_baja (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL,
    entidad            VARCHAR(20)              NOT NULL,
    entidad_id         UUID                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- El pull recorre cada tabla por (establecimiento, updated_at, id)
CREATE INDEX idx_sync_baja_pull ON sync_baja (establecimiento_id, updated_at, id);
CREATE INDEX idx_animal_sync_pull ON animal (establecimiento_id, updated_at, id);
CREATE INDEX idx_evento_sync_pull ON evento (establecimiento_id, updated_at, id);
