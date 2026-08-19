CREATE TABLE stripe_webhook_evento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stripe_event_id VARCHAR(255) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    procesado_at DATETIME NOT NULL,
    CONSTRAINT uk_stripe_webhook_evento_event_id UNIQUE (stripe_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
