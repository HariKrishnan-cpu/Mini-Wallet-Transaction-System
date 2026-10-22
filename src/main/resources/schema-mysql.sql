-- MySQL 8.0.16+ schema (profile "mysql"); CHECK constraints are enforced from 8.0.16
CREATE TABLE IF NOT EXISTS wallet (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)   NOT NULL,
    email       VARCHAR(150)   NOT NULL,
    balance     DECIMAL(19, 2) NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallet_email UNIQUE (email),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS wallet_transaction (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    wallet_id     BIGINT         NOT NULL,
    type          VARCHAR(20)    NOT NULL,
    amount        DECIMAL(19, 2) NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    reference_id  VARCHAR(64)    NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_txn_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT chk_wallet_txn_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_wallet_txn_type CHECK (type IN ('DEPOSIT', 'TRANSFER_IN', 'TRANSFER_OUT')),
    CONSTRAINT chk_wallet_txn_status CHECK (status IN ('SUCCESS', 'FAILED')),
    INDEX idx_wallet_txn_wallet_created (wallet_id, created_at),
    INDEX idx_wallet_txn_reference (reference_id)
) ENGINE = InnoDB;
