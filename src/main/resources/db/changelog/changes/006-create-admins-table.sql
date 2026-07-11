-- src/main/resources/db/changelog/changes/006-create-admins-table.sql
CREATE TABLE admins (
    id BIGSERIAL PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

-- Индекс для быстрого поиска
CREATE INDEX idx_admins_chat_id ON admins(telegram_chat_id);