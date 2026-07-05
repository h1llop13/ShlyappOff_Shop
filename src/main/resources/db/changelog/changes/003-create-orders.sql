-- Таблица заказов
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    delivery_type VARCHAR(50) NOT NULL,
    comment TEXT,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    telegram_user_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

-- Таблица товаров в заказе
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price_at_moment DECIMAL(10, 2) NOT NULL
);

-- Индексы для быстрого поиска
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);