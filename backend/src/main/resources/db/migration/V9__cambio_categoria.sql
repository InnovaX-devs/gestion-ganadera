-- Issue #16: historial de cambios de categoría.
-- Una fila por cada cambio, automático (destete, parto, edad, peso) o manual (edición del animal).

CREATE TABLE cambio_categoria (
    id                  UUID                     PRIMARY KEY,
    organizacion_id     UUID                     NOT NULL REFERENCES organizacion (id),
    -- Si se borra un animal cargado por error, se va con su historial
    animal_id           UUID                     NOT NULL REFERENCES animal (id) ON DELETE CASCADE,
    categoria_anterior  VARCHAR(20)              NOT NULL,
    categoria_nueva     VARCHAR(20)              NOT NULL,
    motivo              VARCHAR(10)              NOT NULL,
    -- Fecha del hecho que provocó el cambio (no la de carga: esa es created_at)
    fecha               DATE                     NOT NULL,
    -- Solo en los cambios manuales: quién lo hizo
    usuario_id          UUID                     REFERENCES usuario (id),

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cambio_categoria_anterior CHECK (categoria_anterior IN
        ('VACA', 'VAQUILLONA', 'TERNERA', 'TERNERO', 'NOVILLO', 'NOVILLITO', 'TORITO', 'TORO')),
    CONSTRAINT ck_cambio_categoria_nueva CHECK (categoria_nueva IN
        ('VACA', 'VAQUILLONA', 'TERNERA', 'TERNERO', 'NOVILLO', 'NOVILLITO', 'TORITO', 'TORO')),
    CONSTRAINT ck_cambio_categoria_motivo CHECK (motivo IN ('DESTETE', 'PARTO', 'EDAD', 'PESO', 'MANUAL')),
    CONSTRAINT ck_cambio_categoria_distinta CHECK (categoria_anterior <> categoria_nueva)
);

CREATE INDEX idx_cambio_categoria_organizacion ON cambio_categoria (organizacion_id);
CREATE INDEX idx_cambio_categoria_animal ON cambio_categoria (animal_id, fecha);
