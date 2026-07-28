--liquibase formatted sql
--changeset shlyapoff:018-performance-indexes dbms:postgresql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_active_created_at
    ON products(created_at DESC) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_active_category_created_at
    ON products(category_id, created_at DESC) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_active_brand_created_at
    ON products(brand_id, created_at DESC) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_active_name_trgm
    ON products USING GIN (LOWER(name) gin_trgm_ops) WHERE is_active = TRUE;
