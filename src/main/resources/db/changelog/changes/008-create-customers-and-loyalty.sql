-- Профили клиентов Telegram Mini App (для истории заказов и программы лояльности)
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    telegram_username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(50),
    total_spent DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_percent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_customers_telegram_user_id ON customers(telegram_user_id);

-- Пороги программы лояльности: сумма заказов -> скидка (%)
CREATE TABLE loyalty_tiers (
    id BIGSERIAL PRIMARY KEY,
    min_amount DECIMAL(12, 2) NOT NULL UNIQUE,
    discount_percent INTEGER NOT NULL
);

-- Связываем заказы с профилем клиента и сохраняем сумму/скидку заказа
ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);
ALTER TABLE orders ADD COLUMN subtotal_amount DECIMAL(10, 2);
ALTER TABLE orders ADD COLUMN discount_percent INTEGER NOT NULL DEFAULT 0;

-- Для уже существующих заказов считаем, что скидки не было, а subtotal = total
UPDATE orders SET subtotal_amount = total_amount WHERE subtotal_amount IS NULL;

CREATE INDEX idx_orders_customer_id ON orders(customer_id);

-- Пример стартовых порогов лояльности (админ может изменить/удалить через /admin/loyalty)
INSERT INTO loyalty_tiers (min_amount, discount_percent) VALUES
    (5000, 5),
    (15000, 10),
    (30000, 15);
