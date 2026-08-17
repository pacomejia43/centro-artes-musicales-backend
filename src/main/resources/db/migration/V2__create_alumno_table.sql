CREATE TABLE alumno (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    telefono VARCHAR(30) NULL,
    fecha_nacimiento DATE NULL,
    fecha_inscripcion DATE NOT NULL,
    google_docs_url VARCHAR(500) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_alumno_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_alumno_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
