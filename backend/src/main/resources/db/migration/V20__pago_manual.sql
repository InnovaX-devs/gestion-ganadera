-- Ningún plan es gratis (issue #35): una cuenta sin prueba, sin suscripción al día y sin pago manual
-- solo puede entrar a Mi plan para pagar. Para quien paga por fuera de Mercado Pago (transferencia,
-- efectivo), el panel de Innovax carga hasta qué día está pago.
ALTER TABLE organizacion ADD COLUMN pago_manual_hasta DATE;
