CREATE TABLE promo_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(255),
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    min_order_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    max_discount_amount DECIMAL(10, 2),
    usage_limit INTEGER,
    per_customer_limit INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP WITHOUT TIME ZONE,
    ends_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_promo_codes_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_promo_codes_value_positive CHECK (discount_value > 0),
    CONSTRAINT chk_promo_codes_min_amount_nonnegative CHECK (min_order_amount >= 0),
    CONSTRAINT chk_promo_codes_max_discount_positive CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    CONSTRAINT chk_promo_codes_usage_limit_positive CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT chk_promo_codes_customer_limit_positive CHECK (per_customer_limit IS NULL OR per_customer_limit > 0),
    CONSTRAINT chk_promo_codes_schedule CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_promo_codes_active_schedule ON promo_codes(active, starts_at, ends_at);

ALTER TABLE orders ADD COLUMN reservation_expires_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE orders ADD COLUMN inventory_reserved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN completed_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE orders ADD COLUMN promo_code_id BIGINT REFERENCES promo_codes(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN promo_code VARCHAR(40);
ALTER TABLE orders ADD COLUMN promo_discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT chk_orders_promo_discount_nonnegative CHECK (promo_discount_amount >= 0);

CREATE INDEX idx_orders_reservation_expiration
    ON orders(status, inventory_reserved, reservation_expires_at);
CREATE INDEX idx_orders_promo_code_id ON orders(promo_code_id);
UPDATE orders SET completed_at = created_at WHERE status = 'COMPLETED';
CREATE INDEX idx_orders_completed_at ON orders(completed_at DESC);
