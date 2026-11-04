-- Suscripciones con Mercado Pago (issue #35, segunda parte). Ver docs/planes.md.
-- No es una tabla de tenant: la actualizan los avisos de Mercado Pago, sin usuario logueado.
CREATE TABLE suscripcion
(
    id              UUID PRIMARY KEY,
    organizacion_id UUID                     NOT NULL REFERENCES organizacion (id),
    plan_codigo     VARCHAR(20)              NOT NULL REFERENCES plan (codigo),
    -- Precio al momento de suscribirse: si después cambia el del plan, esta suscripción sigue igual
    monto           NUMERIC(12, 2)           NOT NULL,
    -- PENDIENTE, ACTIVA, PAUSADA, CANCELADA, VENCIDA o REEMPLAZADA (ver EstadoSuscripcion)
    estado          VARCHAR(20)              NOT NULL,
    -- Id de la suscripción (preapproval) en Mercado Pago y link para pagarla
    mp_id           VARCHAR(64) UNIQUE,
    init_point      VARCHAR(500),
    -- Día en que Mercado Pago la autorizó por primera vez
    activada_el     DATE,
    -- Hasta qué día está pago: un mes después del último cobro aprobado
    pagado_hasta    DATE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suscripcion_organizacion ON suscripcion (organizacion_id);
CREATE INDEX idx_suscripcion_estado ON suscripcion (estado);
