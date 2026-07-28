CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    kind VARCHAR(30) NOT NULL,
    bonus_multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1,
    display_priority INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP WITHOUT TIME ZONE,
    ends_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_promotions_bonus_multiplier_positive CHECK (bonus_multiplier >= 1),
    CONSTRAINT chk_promotions_schedule CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_promotions_active_schedule ON promotions(active, starts_at, ends_at);
