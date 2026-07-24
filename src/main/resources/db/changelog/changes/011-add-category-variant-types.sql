-- Тип варианта задаётся на уровне категории, чтобы не привязывать логику к её названию в коде.
ALTER TABLE categories
    ADD COLUMN variant_type VARCHAR(32) NOT NULL DEFAULT 'NONE';

-- Сохраняем ожидаемое поведение уже существующих базовых категорий.
UPDATE categories
SET variant_type = 'FLAVOR'
WHERE LOWER(TRIM(name)) IN ('одноразки', 'жидкости', 'шайбы');

UPDATE categories
SET variant_type = 'COLOR'
WHERE LOWER(TRIM(name)) IN ('под-системы', 'под системы');

UPDATE categories
SET variant_type = 'COMPATIBILITY'
WHERE LOWER(TRIM(name)) = 'расходники';

-- Варианты теперь могут быть не только вкусами.
ALTER TABLE product_variants
    RENAME COLUMN flavor_name TO variant_value;
