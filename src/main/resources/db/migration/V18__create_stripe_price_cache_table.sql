CREATE TABLE stripe_price_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monto DECIMAL(10,2) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    stripe_product_id VARCHAR(255) NOT NULL,
    stripe_price_id VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_stripe_price_cache_monto_tipo UNIQUE (monto, tipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
