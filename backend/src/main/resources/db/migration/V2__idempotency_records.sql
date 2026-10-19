-- Idempotency records: remember which request a key belongs to so a retry returns the original result
CREATE TABLE idempotency_records (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    transaction_id  UUID REFERENCES transactions (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
