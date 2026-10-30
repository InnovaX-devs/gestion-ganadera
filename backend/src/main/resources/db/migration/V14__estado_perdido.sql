-- Issue #23: la baja de un animal puede ser por muerte o por pérdida (extravío, robo).
-- Se agrega el estado PERDIDO al CHECK de animal.estado. VENDIDO y MUERTO ya existían.
ALTER TABLE animal DROP CONSTRAINT ck_animal_estado;
ALTER TABLE animal ADD CONSTRAINT ck_animal_estado CHECK (estado IN ('ACTIVO', 'VENDIDO', 'MUERTO', 'PERDIDO'));
