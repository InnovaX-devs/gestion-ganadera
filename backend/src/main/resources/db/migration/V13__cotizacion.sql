-- Issue #27: cotizaciones de mercado.
-- Una foto por día y por producto que guarda el job diario (ActualizacionCotizacionesJob). Son datos
-- de mercado compartidos por todas las organizaciones: esta tabla NO tiene organizacion_id.
--
-- - fecha: el día de la foto (cuando corrió el job).
-- - fecha_dato: la fecha que informa la fuente (ej: la pizarra de hoy es la del día hábil anterior,
--   y un remate de invernada puede ser de hace semanas).
-- - estado: ACTUAL (se leyó hoy), ANTERIOR (la fuente falló y se copió el último valor conocido) o
--   SIN_DATO (la fuente falló y nunca hubo un valor).
-- - error: por qué no se pudo leer (solo con ANTERIOR o SIN_DATO). No se muestra en la API.

CREATE TABLE cotizacion (
    id          UUID                     PRIMARY KEY,
    fecha       DATE                     NOT NULL,
    producto    VARCHAR(20)              NOT NULL,
    valor       NUMERIC(14, 2),
    fecha_dato  DATE,
    fuente      VARCHAR(50)              NOT NULL,
    estado      VARCHAR(10)              NOT NULL,
    nota        VARCHAR(150),
    error       VARCHAR(500),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cotizacion_fecha_producto UNIQUE (fecha, producto),
    CONSTRAINT ck_cotizacion_estado CHECK (estado IN ('ACTUAL', 'ANTERIOR', 'SIN_DATO')),
    CONSTRAINT ck_cotizacion_valor CHECK (estado = 'SIN_DATO' OR valor IS NOT NULL)
);

-- Histórico de un producto y "último valor conocido"
CREATE INDEX idx_cotizacion_producto_fecha ON cotizacion (producto, fecha);
