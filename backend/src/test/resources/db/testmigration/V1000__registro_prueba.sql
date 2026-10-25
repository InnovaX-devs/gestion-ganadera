-- Tabla solo para tests: entidad multi-tenant mínima para probar el aislamiento entre organizaciones.
-- Sin FK a organizacion para poder usar ids de organización ficticios.
CREATE TABLE registro_prueba (
    id               UUID                     PRIMARY KEY,
    organizacion_id  UUID                     NOT NULL,
    nombre           VARCHAR(100)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
