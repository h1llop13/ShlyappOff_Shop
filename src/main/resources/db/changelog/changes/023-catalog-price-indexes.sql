--liquibase formatted sql
--changeset shlyapoff:023-catalog-price-indexes dbms:postgresql
CREATE INDEX IF NOT EXISTS idx_products_active_price_id
    ON products(price, id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_active_category_price_id
    ON products(category_id, price, id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_active_brand_price_id
    ON products(brand_id, price, id) WHERE is_active = TRUE;
