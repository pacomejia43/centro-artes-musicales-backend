ALTER TABLE pago_transaccion
    ADD COLUMN stripe_payment_intent_id VARCHAR(255) NULL,
    ADD COLUMN stripe_checkout_session_id VARCHAR(255) NULL,
    ADD COLUMN stripe_invoice_id VARCHAR(255) NULL,
    ADD COLUMN stripe_subscription_id VARCHAR(255) NULL;

CREATE INDEX idx_transaccion_stripe_payment_intent ON pago_transaccion (stripe_payment_intent_id);
CREATE INDEX idx_transaccion_stripe_checkout_session ON pago_transaccion (stripe_checkout_session_id);
CREATE INDEX idx_transaccion_stripe_invoice ON pago_transaccion (stripe_invoice_id);
