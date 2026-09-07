ALTER TABLE orders DROP CONSTRAINT chk_orders_status;

UPDATE orders
SET status = 'CONFIRMED'
WHERE status = 'PROCESSING';

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
    CHECK (status IN (
        'NEW', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'ASSEMBLING',
        'READY', 'SHIPPED', 'COMPLETED', 'CANCELLED'
    ));

ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500);

UPDATE orders
SET cancelled_at = COALESCE(created_at, NOW()),
    cancellation_reason = 'Причина не указана (перенесено из старой версии)'
WHERE status = 'CANCELLED';

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_cancellation_data
    CHECK (
        (status <> 'CANCELLED' AND cancellation_reason IS NULL AND cancelled_at IS NULL)
        OR
        (status = 'CANCELLED' AND cancellation_reason IS NOT NULL AND cancelled_at IS NOT NULL)
    );

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    changed_by VARCHAR(255) NOT NULL,
    cancellation_reason VARCHAR(500),
    CONSTRAINT chk_order_status_history_previous CHECK (
        previous_status IS NULL OR previous_status IN (
            'NEW', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'ASSEMBLING',
            'READY', 'SHIPPED', 'COMPLETED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_order_status_history_new CHECK (
        new_status IN (
            'NEW', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'ASSEMBLING',
            'READY', 'SHIPPED', 'COMPLETED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_order_status_history_cancellation CHECK (
        new_status = 'CANCELLED' OR cancellation_reason IS NULL
    )
);

CREATE INDEX idx_order_status_history_order_time
    ON order_status_history(order_id, changed_at, id);

INSERT INTO order_status_history (
    order_id, previous_status, new_status, changed_at, changed_by, cancellation_reason
)
SELECT id, NULL, status,
       COALESCE(cancelled_at, completed_at, created_at, NOW()),
       'migration', cancellation_reason
FROM orders;

ALTER TABLE notification_outbox
    ADD COLUMN notification_type VARCHAR(40) NOT NULL DEFAULT 'ADMIN_NEW_ORDER';
ALTER TABLE notification_outbox ADD COLUMN previous_status VARCHAR(32);
ALTER TABLE notification_outbox ADD COLUMN target_status VARCHAR(32);
ALTER TABLE notification_outbox ADD COLUMN cancellation_reason VARCHAR(500);

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_type
    CHECK (notification_type IN ('ADMIN_NEW_ORDER', 'CUSTOMER_STATUS_CHANGED'));

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_outbox_status_event
    CHECK (
        (notification_type = 'ADMIN_NEW_ORDER' AND previous_status IS NULL AND target_status IS NULL)
        OR
        (notification_type = 'CUSTOMER_STATUS_CHANGED' AND target_status IN (
            'NEW', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID', 'ASSEMBLING',
            'READY', 'SHIPPED', 'COMPLETED', 'CANCELLED'
        ))
    );

CREATE INDEX idx_notification_outbox_order_type
    ON notification_outbox(order_id, notification_type, id);
