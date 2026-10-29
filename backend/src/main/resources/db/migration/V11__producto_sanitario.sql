-- Issue #20: tratamientos sanitarios.
-- Catálogo de productos (vacunas, antiparasitarios, antibióticos...) que cada establecimiento
-- reutiliza al registrar tratamientos. Los datos de cada aplicación (dosis, vía, veterinario,
-- días de carencia) viven en el evento (evento.datos), no acá; el catálogo solo guarda los valores
-- habituales para completar el formulario.
--
-- - dias_carencia: días que tienen que pasar desde la aplicación para poder faenar o vender al
--   animal. 0 = sin carencia. Máximo 365 (el cálculo de carencia mira un año hacia atrás).
-- - activo: un producto no se borra, se da de baja, porque los tratamientos ya registrados lo nombran.

CREATE TABLE producto_sanitario (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    nombre             VARCHAR(100)             NOT NULL,
    tipo               VARCHAR(20)              NOT NULL,
    dias_carencia      INTEGER                  NOT NULL DEFAULT 0,
    dosis_habitual     VARCHAR(100),
    via_habitual       VARCHAR(20),
    activo             BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_producto_sanitario_tipo CHECK (tipo IN ('VACUNA', 'ANTIPARASITARIO', 'ANTIBIOTICO', 'OTRO')),
    CONSTRAINT ck_producto_sanitario_carencia CHECK (dias_carencia BETWEEN 0 AND 365)
);

CREATE INDEX idx_producto_sanitario_organizacion ON producto_sanitario (organizacion_id);
CREATE INDEX idx_producto_sanitario_establecimiento ON producto_sanitario (establecimiento_id, activo);
