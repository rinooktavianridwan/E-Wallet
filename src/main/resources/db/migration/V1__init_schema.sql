-- Users: authentication and basic profile
CREATE TABLE users (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Wallets: one wallet per user
CREATE TABLE wallets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT         NOT NULL UNIQUE REFERENCES users (id),
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    is_active  BOOLEAN        NOT NULL DEFAULT TRUE,
    version    BIGINT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
);

-- Transactions: financial ledger (double-entry style: a transfer = one DEBIT row + one CREDIT row sharing reference_number)
CREATE TABLE transactions (
    id               UUID PRIMARY KEY,
    wallet_id        BIGINT         NOT NULL REFERENCES wallets (id),
    transaction_type VARCHAR(10)    NOT NULL,
    category         VARCHAR(20)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL,
    balance_after    NUMERIC(19, 2) NOT NULL,
    reference_number VARCHAR(50)    NOT NULL,
    description      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_tx_type CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_tx_category CHECK (category IN ('TOP_UP', 'TRANSFER', 'WITHDRAW')),
    CONSTRAINT uq_tx_reference_wallet UNIQUE (reference_number, wallet_id)
);

CREATE INDEX idx_tx_wallet_created ON transactions (wallet_id, created_at DESC);
CREATE INDEX idx_tx_reference ON transactions (reference_number);
