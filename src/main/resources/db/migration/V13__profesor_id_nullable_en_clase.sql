-- Al eliminar un profesor, sus clases se reasignan a otro profesor o, si no hay ninguno más,
-- se dejan sin profesor asignado (ver ProfesorService#eliminar) en vez de bloquear el borrado.
ALTER TABLE clase MODIFY COLUMN profesor_id BIGINT NULL;
