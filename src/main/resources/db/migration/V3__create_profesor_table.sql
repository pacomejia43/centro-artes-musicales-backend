CREATE TABLE profesor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    telefono VARCHAR(30) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE profesor_especialidad (
    profesor_id BIGINT NOT NULL,
    instrumento VARCHAR(40) NOT NULL,
    PRIMARY KEY (profesor_id, instrumento),
    CONSTRAINT fk_especialidad_profesor FOREIGN KEY (profesor_id) REFERENCES profesor (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
