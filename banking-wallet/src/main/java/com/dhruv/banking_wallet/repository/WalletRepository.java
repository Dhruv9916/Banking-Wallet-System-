package com.dhruv.banking_wallet.repository;

import com.dhruv.banking_wallet.dto.WalletSummaryResponse;
import com.dhruv.banking_wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {


    @Query("""
    SELECT DISTINCT w
    FROM Wallet w
    LEFT JOIN FETCH w.transactions
""")
    List<Wallet> findAllWithTransactions();


    @EntityGraph(attributePaths = "transactions")
    @Query("SELECT w FROM Wallet w")
    List<Wallet> findAllWithTransactionsUsingEntityGraph();


    @Query("""
    SELECT new com.dhruv.banking_wallet.dto.WalletSummaryResponse(
        w.id,
        w.ownerName,
        w.balance,
        w.currency,
        COUNT(t)
    )
    FROM Wallet w
    LEFT JOIN w.transactions t
    GROUP BY w.id, w.ownerName, w.balance, w.currency
""")
    List<WalletSummaryResponse> findWalletSummaries();


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM Wallet w
            WHERE w.id = :walletId
            """)
    Optional<Wallet> findByIdForUpdate(@Param("walletId") Long walletId);
}