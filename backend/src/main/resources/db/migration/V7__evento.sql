-- Issue #17: eventos. Todo lo que le pasa a un animal (o a un lote, o al establecimiento) queda
-- registrado acá: nacimientos, servicios, tactos, tratamientos, pesajes, movimientos, ventas,
-- bajas y observaciones.
--
-- - Un evento sobre varios animales se guarda como una fila por animal, todas con el mismo
--   operacion_id (así cada animal tiene su historial completo y la carga grupal se puede ver junta).
-- - operacion_id puede venir del cliente: si se reenvía la misma operación (app offline que
--   reintenta), no se duplica.
-- - datos: JSON con lo propio de cada tipo (texto de la observación, kilos del pesaje...).
--   VARCHAR en vez de JSONB para que la migración sea la misma en PostgreSQL y en H2.
-- - deleted_at: los eventos no se borran, se anulan (queda el registro para auditoría).

CREATE TABLE evento (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    operacion_id       UUID                     NOT NULL,
    tipo               VARCHAR(30)              NOT NULL,
    fecha              DATE                     NOT NULL,
    animal_id          UUID                     REFERENCES animal (id),
    lote_id            UUID                     REFERENCES lote (id),
    datos              VARCHAR(10000)           NOT NULL,
    usuario_id         UUID                     NOT NULL REFERENCES usuario (id),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_evento_organizacion ON evento (organizacion_id);
CREATE INDEX idx_evento_establecimiento_fecha ON evento (establecimiento_id, fecha);
CREATE INDEX idx_evento_animal_fecha ON evento (animal_id, fecha);
CREATE INDEX idx_evento_lote ON evento (lote_id);
CREATE INDEX idx_evento_operacion ON evento (operacion_id);
