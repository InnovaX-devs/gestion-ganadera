-- Issue #25: stock calculado.
-- El stock sale de una sola consulta agregada sobre animal (ver StockRepository), que filtra por
-- establecimiento + estado y agrupa por categoría, sexo, lote y rodeo. Con este índice la base
-- resuelve esa consulta leyendo solo el índice, sin recorrer la tabla.
CREATE INDEX idx_animal_stock ON animal (establecimiento_id, estado, categoria, sexo, lote_id, rodeo_id);
