-- Issue #35: planes y límites. El cobro (Mercado Pago) viene después: por ahora el precio queda vacío.
-- Un límite NULL es "sin límite".

CREATE TABLE plan (
    codigo                VARCHAR(20)   PRIMARY KEY,
    nombre                VARCHAR(50)   NOT NULL,
    orden                 INTEGER       NOT NULL,
    max_animales          INTEGER,
    max_establecimientos  INTEGER,
    max_usuarios          INTEGER,
    precio_mensual        NUMERIC(12, 2)
);

INSERT INTO plan (codigo, nombre, orden, max_animales, max_establecimientos, max_usuarios) VALUES
    ('INICIAL',   'Inicial',   1,  200,  1,    2),
    ('PRODUCTOR', 'Productor', 2,  1000, 2,    4),
    ('EMPRESA',   'Empresa',   3,  NULL, NULL, NULL);

-- Plan contratado. Mientras dura la prueba gratis (prueba_hasta) rigen los límites del plan de prueba.
-- Las organizaciones que ya existían quedan en Inicial y sin prueba.
ALTER TABLE organizacion ADD COLUMN plan_codigo VARCHAR(20) DEFAULT 'INICIAL' NOT NULL;
ALTER TABLE organizacion ADD COLUMN prueba_hasta DATE;
ALTER TABLE organizacion ADD CONSTRAINT fk_organizacion_plan FOREIGN KEY (plan_codigo) REFERENCES plan (codigo);
