ALTER TABLE orders
    ADD COLUMN delivery_amount DECIMAL(10, 2) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_delivery_amount_nonnegative CHECK (delivery_amount >= 0);

CREATE TABLE admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_username VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_log_entity ON admin_audit_log(entity_type, entity_id);
CREATE INDEX idx_admin_audit_log_actor ON admin_audit_log(actor_username, created_at DESC);
