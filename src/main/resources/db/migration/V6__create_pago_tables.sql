CREATE TABLE pago (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    periodo CHAR(7) NOT NULL,
    fecha_limite DATE NOT NULL,
    notas VARCHAR(500) NULL,
    estado VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_pago_alumno_periodo UNIQUE (alumno_id, periodo),
    CONSTRAINT fk_pago_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pago_transaccion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pago_id BIGINT NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    fecha DATETIME NOT NULL,
    metodo_pago VARCHAR(20) NOT NULL,
    referencia VARCHAR(255) NULL,
    estado VARCHAR(20) NOT NULL,
    motivo_rechazo VARCHAR(500) NULL,
    revisado_por BIGINT NULL,
    revisado_at DATETIME NULL,
    registrado_por BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_transaccion_pago FOREIGN KEY (pago_id) REFERENCES pago (id),
    CONSTRAINT fk_transaccion_revisado_por FOREIGN KEY (revisado_por) REFERENCES usuario (id),
    CONSTRAINT fk_transaccion_registrado_por FOREIGN KEY (registrado_por) REFERENCES usuario (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_transaccion_pago ON pago_transaccion (pago_id);
