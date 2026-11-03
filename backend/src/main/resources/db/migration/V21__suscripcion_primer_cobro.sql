-- Primer cobro de la suscripción (issue #35): si alguien se suscribe durante la prueba gratis (o con
-- un pago manual vigente), Mercado Pago cobra recién el día después de que eso termina.
ALTER TABLE suscripcion ADD COLUMN primer_cobro DATE;
