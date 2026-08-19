CREATE TABLE suscripcion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL,
    stripe_price_id VARCHAR(255) NOT NULL,
    stripe_customer_id VARCHAR(255) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    cancelacion_programada TINYINT(1) NOT NULL DEFAULT 0,
    fecha_cancelacion DATE NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_suscripcion_alumno UNIQUE (alumno_id),
    CONSTRAINT uk_suscripcion_stripe_subscription_id UNIQUE (stripe_subscription_id),
    CONSTRAINT fk_suscripcion_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
