CREATE TABLE solicitud_reagendacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    clase_id BIGINT NOT NULL,
    fecha_hora_propuesta DATETIME NOT NULL,
    profesor_propuesto_id BIGINT NULL,
    motivo VARCHAR(500) NULL,
    estado VARCHAR(20) NOT NULL,
    motivo_rechazo VARCHAR(500) NULL,
    revisado_por BIGINT NULL,
    revisado_at DATETIME NULL,
    clase_nueva_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_solicitud_clase FOREIGN KEY (clase_id) REFERENCES clase (id),
    CONSTRAINT fk_solicitud_profesor_propuesto FOREIGN KEY (profesor_propuesto_id) REFERENCES profesor (id),
    CONSTRAINT fk_solicitud_revisado_por FOREIGN KEY (revisado_por) REFERENCES usuario (id),
    CONSTRAINT fk_solicitud_clase_nueva FOREIGN KEY (clase_nueva_id) REFERENCES clase (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_solicitud_estado ON solicitud_reagendacion (estado);
