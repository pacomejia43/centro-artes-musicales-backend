CREATE TABLE clase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    profesor_id BIGINT NOT NULL,
    instrumento VARCHAR(40) NOT NULL,
    fecha_hora DATETIME NOT NULL,
    duracion_minutos INT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    clase_original_id BIGINT NULL,
    notas VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_clase_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id),
    CONSTRAINT fk_clase_profesor FOREIGN KEY (profesor_id) REFERENCES profesor (id),
    CONSTRAINT fk_clase_original FOREIGN KEY (clase_original_id) REFERENCES clase (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_clase_profesor_fecha ON clase (profesor_id, fecha_hora);
CREATE INDEX idx_clase_alumno_fecha ON clase (alumno_id, fecha_hora);
