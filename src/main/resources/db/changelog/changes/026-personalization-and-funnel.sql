ALTER TABLE promotions ADD COLUMN promo_code_id BIGINT REFERENCES promo_codes(id) ON DELETE SET NULL;
ALTER TABLE promotions ADD COLUMN terms TEXT;
CREATE INDEX idx_promotions_promo_code_id ON promotions(promo_code_id);

CREATE TABLE shopping_events (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    telegram_user_id BIGINT,
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    event_type VARCHAR(32) NOT NULL,
    abandonment_reason VARCHAR(40),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_shopping_events_type CHECK (event_type IN (
        'PRODUCT_VIEW', 'CART_ADD', 'CHECKOUT_STARTED', 'ORDER_CREATED', 'CART_ABANDONED'
    )),
    CONSTRAINT chk_shopping_events_reason CHECK (
        event_type = 'CART_ABANDONED' OR abandonment_reason IS NULL
    )
);

CREATE INDEX idx_shopping_events_session_time ON shopping_events(session_id, created_at DESC);
CREATE INDEX idx_shopping_events_telegram_time ON shopping_events(telegram_user_id, created_at DESC);
CREATE INDEX idx_shopping_events_product_time ON shopping_events(product_id, created_at DESC);
CREATE INDEX idx_shopping_events_type_time ON shopping_events(event_type, created_at DESC);

CREATE TABLE stock_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    queued_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_stock_subscriptions_product_user UNIQUE (product_id, telegram_user_id)
);

CREATE INDEX idx_stock_subscriptions_pending ON stock_subscriptions(queued_at, product_id);

ALTER TABLE notification_outbox ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE notification_outbox ADD COLUMN product_id BIGINT REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE notification_outbox ADD COLUMN promo_code_id BIGINT REFERENCES promo_codes(id) ON DELETE CASCADE;
ALTER TABLE notification_outbox ADD COLUMN telegram_user_id BIGINT;

ALTER TABLE notification_outbox DROP CONSTRAINT chk_notification_outbox_type;
ALTER TABLE notification_outbox DROP CONSTRAINT chk_notification_outbox_status_event;

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_type
    CHECK (notification_type IN (
        'ADMIN_NEW_ORDER', 'CUSTOMER_STATUS_CHANGED', 'STOCK_AVAILABLE', 'PERSONAL_PROMO_CODE'
    ));

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_payload
    CHECK (
        (notification_type = 'ADMIN_NEW_ORDER'
            AND order_id IS NOT NULL AND product_id IS NULL AND promo_code_id IS NULL
            AND previous_status IS NULL AND target_status IS NULL)
        OR
        (notification_type = 'CUSTOMER_STATUS_CHANGED'
            AND order_id IS NOT NULL AND product_id IS NULL AND promo_code_id IS NULL
            AND target_status IN (
                'NEW', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'ASSEMBLING',
                'READY', 'SHIPPED', 'COMPLETED', 'CANCELLED'
            ))
        OR
        (notification_type = 'STOCK_AVAILABLE'
            AND order_id IS NULL AND product_id IS NOT NULL AND promo_code_id IS NULL
            AND telegram_user_id IS NOT NULL AND previous_status IS NULL AND target_status IS NULL)
        OR
        (notification_type = 'PERSONAL_PROMO_CODE'
            AND order_id IS NULL AND product_id IS NULL AND promo_code_id IS NOT NULL
            AND telegram_user_id IS NOT NULL AND previous_status IS NULL AND target_status IS NULL)
    );

CREATE INDEX idx_notification_outbox_product ON notification_outbox(product_id, notification_type, id);
CREATE INDEX idx_notification_outbox_promo_code ON notification_outbox(promo_code_id, notification_type, id);
