ALTER TABLE products ADD COLUMN low_stock_threshold INTEGER NOT NULL DEFAULT 5;
ALTER TABLE products ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE products ADD COLUMN publish_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE products ADD CONSTRAINT chk_products_low_stock_threshold_nonnegative CHECK (low_stock_threshold >= 0);
ALTER TABLE products ADD CONSTRAINT chk_products_publication_status CHECK (publication_status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED'));
ALTER TABLE products ADD CONSTRAINT chk_products_publication_schedule CHECK (publication_status <> 'SCHEDULED' OR publish_at IS NOT NULL);

UPDATE products SET publication_status = 'DRAFT' WHERE is_active = FALSE;

ALTER TABLE promotions ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE promotions ADD COLUMN publish_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE promotions ADD CONSTRAINT chk_promotions_publication_status CHECK (publication_status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED'));
ALTER TABLE promotions ADD CONSTRAINT chk_promotions_publication_schedule CHECK (publication_status <> 'SCHEDULED' OR publish_at IS NOT NULL);

UPDATE promotions SET publication_status = 'DRAFT' WHERE active = FALSE;

CREATE TABLE inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_variant_id BIGINT REFERENCES product_variants(id) ON DELETE SET NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    quantity_delta INTEGER NOT NULL,
    reason VARCHAR(500),
    actor_username VARCHAR(255) NOT NULL,
    reference_type VARCHAR(40),
    reference_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_movement_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'MANUAL_ADJUSTMENT', 'CSV_IMPORT',
        'ORDER_RESERVATION', 'RESERVATION_RELEASE'
    )),
    CONSTRAINT chk_inventory_movement_quantities CHECK (
        quantity_before >= 0 AND quantity_after >= 0
        AND quantity_after - quantity_before = quantity_delta
    )
);

CREATE INDEX idx_inventory_movements_created_at ON inventory_movements(created_at DESC);
CREATE INDEX idx_inventory_movements_product ON inventory_movements(product_id, created_at DESC);
CREATE INDEX idx_inventory_movements_variant ON inventory_movements(product_variant_id, created_at DESC);
CREATE INDEX idx_products_inventory_alerts ON products(is_active, low_stock_threshold, stock_quantity);
CREATE INDEX idx_products_publication ON products(publication_status, publish_at);
CREATE INDEX idx_promotions_publication ON promotions(publication_status, publish_at);
