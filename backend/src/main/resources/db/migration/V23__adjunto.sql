-- Issue #43: fotos y archivos adjuntos a eventos y animales. Ver docs/adjuntos.md y la decisión 0005.
--
-- - El archivo en sí NO está en la base: está en el almacén de archivos (hoy, disco) bajo `clave`.
--   Acá queda el registro: de quién es, cómo se llama, qué es y cuánto pesa.
-- - Un adjunto es de un animal (animal_id) o de una operación de eventos (operacion_id), nunca de los dos.
--   Se cuelga de la operación y no de un evento: una observación sobre 30 animales son 30 filas en
--   `evento` con el mismo operacion_id, y la foto se sube una sola vez y se ve en todas.
--   operacion_id no tiene FK porque en `evento` se repite (una fila por animal).
-- - carga_id lo genera el celular: si se pierde la respuesta y reintenta la subida, no se duplica
--   (igual que operacion_id en los eventos).

CREATE TABLE adjunto (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    operacion_id       UUID,
    animal_id          UUID                     REFERENCES animal (id),
    carga_id           UUID                     NOT NULL,
    nombre             VARCHAR(150)             NOT NULL,
    tipo_contenido     VARCHAR(50)              NOT NULL,
    tamanio            BIGINT                   NOT NULL,
    clave              VARCHAR(100)             NOT NULL,
    usuario_id         UUID                     NOT NULL REFERENCES usuario (id),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_adjunto_de_una_sola_cosa CHECK (
        (operacion_id IS NOT NULL AND animal_id IS NULL)
        OR (operacion_id IS NULL AND animal_id IS NOT NULL))
);

CREATE INDEX idx_adjunto_organizacion ON adjunto (organizacion_id);
CREATE INDEX idx_adjunto_operacion ON adjunto (operacion_id);
CREATE INDEX idx_adjunto_animal ON adjunto (animal_id);
CREATE UNIQUE INDEX uq_adjunto_carga ON adjunto (organizacion_id, carga_id);
