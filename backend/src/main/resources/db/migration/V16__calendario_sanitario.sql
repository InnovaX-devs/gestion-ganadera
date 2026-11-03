-- Issue #34: calendario sanitario y alertas. Ver docs/calendario-sanitario.md.

-- Tratamientos planificados para una fecha: de un lote, de un rodeo o de todo el establecimiento.
CREATE TABLE tratamiento_planificado (
    id                      UUID                     PRIMARY KEY,
    organizacion_id         UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id      UUID                     NOT NULL REFERENCES establecimiento (id),
    fecha                   DATE                     NOT NULL,
    -- Sin lote ni rodeo = todo el establecimiento
    lote_id                 UUID                     REFERENCES lote (id),
    rodeo_id                UUID                     REFERENCES rodeo (id),
    producto_id             UUID                     REFERENCES producto_sanitario (id),
    descripcion             VARCHAR(200)             NOT NULL,
    dosis                   VARCHAR(100),
    notas                   VARCHAR(500),
    -- Cuántos días antes llega el aviso por email (0 = el mismo día)
    avisar_dias_antes       INTEGER                  NOT NULL DEFAULT 1,
    estado                  VARCHAR(12)              NOT NULL DEFAULT 'PENDIENTE',
    -- Cuando se hizo: la operación del tratamiento registrado (si se registró desde el calendario)
    realizado_operacion_id  UUID,
    realizado_en            TIMESTAMP WITH TIME ZONE,
    creado_por              UUID                     REFERENCES usuario (id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_plan_estado CHECK (estado IN ('PENDIENTE', 'REALIZADO', 'CANCELADO')),
    CONSTRAINT ck_plan_destino CHECK (lote_id IS NULL OR rodeo_id IS NULL),
    CONSTRAINT ck_plan_aviso CHECK (avisar_dias_antes BETWEEN 0 AND 30)
);

CREATE INDEX idx_plan_establecimiento_fecha ON tratamiento_planificado (establecimiento_id, fecha);

-- Si el usuario quiere recibir las alertas por email
ALTER TABLE usuario ADD COLUMN alertas_email BOOLEAN NOT NULL DEFAULT TRUE;

-- Un aviso por día, por establecimiento y por usuario: si el job corre dos veces, no se repite.
CREATE TABLE alerta_enviada (
    id                 UUID                     PRIMARY KEY,
    organizacion_id    UUID                     NOT NULL REFERENCES organizacion (id),
    establecimiento_id UUID                     NOT NULL REFERENCES establecimiento (id),
    usuario_id         UUID                     NOT NULL REFERENCES usuario (id),
    fecha              DATE                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_alerta_enviada UNIQUE (establecimiento_id, usuario_id, fecha)
);
