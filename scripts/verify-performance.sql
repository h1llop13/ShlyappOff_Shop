-- Run on the VPS after loading representative catalog data:
-- docker compose exec -T db psql -U shlyapoff_user -d shlyapoff_db < scripts/verify-performance.sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name, p.description, p.price, p.stock_quantity, p.image_url,
       p.image_thumbnail_url, c.variant_type
FROM products p
LEFT JOIN categories c ON c.id = p.category_id
WHERE p.is_active = TRUE
ORDER BY p.created_at DESC
LIMIT 12;

EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name
FROM products p
WHERE p.is_active = TRUE
  AND lower(p.name) LIKE '%vape%'
ORDER BY p.created_at DESC
LIMIT 12;

EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name
FROM products p
WHERE p.is_active = TRUE
  AND p.category_id = (SELECT id FROM categories ORDER BY id LIMIT 1)
ORDER BY p.created_at DESC
LIMIT 12;

EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.name
FROM products p
WHERE p.is_active = TRUE
  AND p.brand_id = (SELECT id FROM brands ORDER BY id LIMIT 1)
ORDER BY p.created_at DESC
LIMIT 12;
