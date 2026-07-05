-- Индексы для ускорения поиска корзины по сессии
CREATE INDEX IF NOT EXISTS idx_carts_session_id ON carts(session_id);

-- Индексы для быстрого поиска товаров в корзине
CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_product_id ON cart_items(product_id);

-- Индексы для товаров
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_brand_id ON products(brand_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);