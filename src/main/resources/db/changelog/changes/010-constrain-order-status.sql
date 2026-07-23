UPDATE orders
SET status = 'NEW'
WHERE status NOT IN ('NEW', 'PROCESSING', 'COMPLETED', 'CANCELLED');

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('NEW', 'PROCESSING', 'COMPLETED', 'CANCELLED'));
