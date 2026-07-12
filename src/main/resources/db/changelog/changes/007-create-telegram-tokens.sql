-- Таблица для одноразовых токенов входа через Telegram
CREATE TABLE telegram_login_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    telegram_chat_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

-- Индекс для быстрого поиска токена
CREATE INDEX idx_telegram_tokens_token ON telegram_login_tokens(token);