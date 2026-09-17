package com.dhruv.banking_wallet.exception;

public class WalletConcurrentModificationException extends RuntimeException {

    public WalletConcurrentModificationException() {
        super("Wallet was modified by another transaction. Please retry.");
    }
}