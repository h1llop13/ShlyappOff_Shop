ALTER TABLE cart_items ADD COLUMN product_variant_id BIGINT REFERENCES product_variants(id);
ALTER TABLE cart_items ADD COLUMN variant_key BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN variant_value VARCHAR(255);

ALTER TABLE cart_items
    ADD CONSTRAINT uq_cart_items_product_variant UNIQUE (cart_id, product_id, variant_key);

ALTER TABLE products
    ADD CONSTRAINT chk_products_price_nonnegative CHECK (price >= 0);

ALTER TABLE cart_items
    ADD CONSTRAINT chk_cart_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE loyalty_tiers
    ADD CONSTRAINT chk_loyalty_tiers_min_amount_nonnegative CHECK (min_amount >= 0);
ALTER TABLE loyalty_tiers
    ADD CONSTRAINT chk_loyalty_tiers_discount_range CHECK (discount_percent BETWEEN 0 AND 100);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_subtotal_nonnegative CHECK (subtotal_amount >= 0);
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_total_nonnegative CHECK (total_amount >= 0);
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_discount_range CHECK (discount_percent BETWEEN 0 AND 100);
