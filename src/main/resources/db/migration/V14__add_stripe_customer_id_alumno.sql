ALTER TABLE alumno ADD COLUMN stripe_customer_id VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_alumno_stripe_customer_id ON alumno (stripe_customer_id);
