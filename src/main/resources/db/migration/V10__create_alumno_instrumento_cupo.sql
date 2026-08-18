CREATE TABLE alumno_instrumento_cupo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    instrumento VARCHAR(40) NOT NULL,
    cupo_mensual INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_alumno_instrumento UNIQUE (alumno_id, instrumento),
    CONSTRAINT fk_alumno_instrumento_cupo_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
