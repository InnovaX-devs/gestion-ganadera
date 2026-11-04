-- Issue #13: animales.
-- Cada animal pertenece a un establecimiento y, opcionalmente, a un lote y a un rodeo del mismo.
-- Sexo, categoría y estado se guardan como texto (ver EnumTextoConverter).

CREATE TABLE animal (
    id                        UUID                     PRIMARY KEY,
    organizacion_id           UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id        UUID                     NOT NULL REFERENCES establecimiento (id),
    lote_id                   UUID                     REFERENCES lote (id),
    rodeo_id                  UUID                     REFERENCES rodeo (id),

    -- Caravana visual (la que se lee en el campo) y electrónica (RFID / SENASA), siempre en mayúsculas
    caravana                  VARCHAR(30)              NOT NULL,
    caravana_electronica      VARCHAR(30),

    sexo                      VARCHAR(10)              NOT NULL,
    categoria                 VARCHAR(20)              NOT NULL,
    raza                      VARCHAR(50),
    fecha_nacimiento          DATE,
    -- TRUE si la fecha es aproximada (animales comprados o sin registro de nacimiento)
    fecha_nacimiento_estimada BOOLEAN                  NOT NULL DEFAULT FALSE,
    castrado                  BOOLEAN                  NOT NULL DEFAULT FALSE,

    madre_id                  UUID                     REFERENCES animal (id),
    padre_id                  UUID                     REFERENCES animal (id),

    -- ACTIVO: está en el stock. VENDIDO / MUERTO: lo cambian los eventos de venta y baja (#23)
    estado                    VARCHAR(10)              NOT NULL DEFAULT 'ACTIVO',
    observaciones             VARCHAR(500),

    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_animal_sexo CHECK (sexo IN ('HEMBRA', 'MACHO')),
    CONSTRAINT ck_animal_categoria CHECK (categoria IN
        ('VACA', 'VAQUILLONA', 'TERNERA', 'TERNERO', 'NOVILLO', 'NOVILLITO', 'TORITO', 'TORO')),
    CONSTRAINT ck_animal_estado CHECK (estado IN ('ACTIVO', 'VENDIDO', 'MUERTO')),
    CONSTRAINT ck_animal_no_es_su_madre CHECK (madre_id IS NULL OR madre_id <> id),
    CONSTRAINT ck_animal_no_es_su_padre CHECK (padre_id IS NULL OR padre_id <> id)
);

CREATE INDEX idx_animal_organizacion ON animal (organizacion_id);
CREATE INDEX idx_animal_establecimiento_caravana ON animal (establecimiento_id, caravana);
CREATE INDEX idx_animal_caravana_electronica ON animal (caravana_electronica);
CREATE INDEX idx_animal_lote ON animal (lote_id);
CREATE INDEX idx_animal_rodeo ON animal (rodeo_id);
CREATE INDEX idx_animal_madre ON animal (madre_id);
CREATE INDEX idx_animal_padre ON animal (padre_id);
