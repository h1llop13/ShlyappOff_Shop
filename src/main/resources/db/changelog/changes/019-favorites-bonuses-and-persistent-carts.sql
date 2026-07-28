-- Избранное, постоянные корзины и бонусная программа.
ALTER TABLE carts ADD COLUMN telegram_user_id BIGINT UNIQUE;
CREATE INDEX idx_carts_telegram_user_id ON carts(telegram_user_id);

CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_favorites_customer_product UNIQUE (customer_id, product_id)
);
CREATE INDEX idx_favorites_customer_id ON favorites(customer_id);

ALTER TABLE customers ADD COLUMN bonus_balance DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE loyalty_tiers RENAME COLUMN discount_percent TO bonus_percent;
ALTER TABLE loyalty_tiers DROP CONSTRAINT IF EXISTS chk_loyalty_tiers_discount_range;
ALTER TABLE loyalty_tiers ADD CONSTRAINT chk_loyalty_tiers_bonus_range CHECK (bonus_percent BETWEEN 0 AND 100);

ALTER TABLE orders ADD COLUMN bonuses_spent DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN bonuses_earned DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD CONSTRAINT chk_orders_bonuses_spent_nonnegative CHECK (bonuses_spent >= 0);
ALTER TABLE orders ADD CONSTRAINT chk_orders_bonuses_earned_nonnegative CHECK (bonuses_earned >= 0);
