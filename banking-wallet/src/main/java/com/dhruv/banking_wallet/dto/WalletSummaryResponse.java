package com.dhruv.banking_wallet.dto;

import java.math.BigDecimal;

public record WalletSummaryResponse(
        Long id,
        String ownerName,
        BigDecimal balance,
        String currency,
        Long transactionCount
) {
}