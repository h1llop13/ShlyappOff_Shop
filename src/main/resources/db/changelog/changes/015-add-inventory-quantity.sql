ALTER TABLE products
    ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE product_variants
    ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0;

UPDATE product_variants
SET in_stock = FALSE
WHERE stock_quantity = 0;

ALTER TABLE order_items
    ADD COLUMN product_variant_id BIGINT REFERENCES product_variants(id);

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_quantity_nonnegative CHECK (stock_quantity >= 0);

ALTER TABLE product_variants
    ADD CONSTRAINT chk_product_variants_stock_quantity_nonnegative CHECK (stock_quantity >= 0);
