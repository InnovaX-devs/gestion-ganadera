-- Issue #18: evento Nacimiento.
-- Las crías que crea un nacimiento quedan vinculadas a su evento. Así, si el nacimiento se anula
-- (cargado por error), se sabe exactamente qué animales borrar.
-- Los nacidos muertos no se crean como animales: quedan solo en los datos del evento.

ALTER TABLE animal ADD COLUMN evento_nacimiento_id UUID REFERENCES evento (id);

CREATE INDEX idx_animal_evento_nacimiento ON animal (evento_nacimiento_id);
