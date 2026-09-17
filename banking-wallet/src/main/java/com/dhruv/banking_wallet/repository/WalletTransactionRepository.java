package com.dhruv.banking_wallet.repository;

import com.dhruv.banking_wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletId(Long walletId);

    Optional<WalletTransaction> findById(Long id);
}