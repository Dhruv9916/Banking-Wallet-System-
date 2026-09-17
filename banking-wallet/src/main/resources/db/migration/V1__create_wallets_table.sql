CREATE TABLE wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_name VARCHAR(100) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,

    PRIMARY KEY (id)
);