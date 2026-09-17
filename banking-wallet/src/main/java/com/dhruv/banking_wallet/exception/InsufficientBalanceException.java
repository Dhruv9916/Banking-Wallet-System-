package com.dhruv.banking_wallet.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(BigDecimal balance, BigDecimal amount) {
        super("Insufficient wallet balance. Current balance: "
                + balance + ", withdrawal amount: " + amount);
    }
}