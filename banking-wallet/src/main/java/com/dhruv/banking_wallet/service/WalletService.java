package com.dhruv.banking_wallet.service;

import com.dhruv.banking_wallet.dto.MoneyRequest;
import com.dhruv.banking_wallet.dto.TransferRequest;
import com.dhruv.banking_wallet.dto.WalletRequest;
import com.dhruv.banking_wallet.dto.WalletResponse;
import com.dhruv.banking_wallet.entity.Wallet;
import com.dhruv.banking_wallet.entity.WalletTransaction;
import com.dhruv.banking_wallet.entity.WalletTransactionType;
import com.dhruv.banking_wallet.repository.WalletRepository;
import com.dhruv.banking_wallet.repository.WalletTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.dhruv.banking_wallet.exception.WalletNotFoundException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.dhruv.banking_wallet.exception.InsufficientBalanceException;


@Service
public class WalletService {

    @PersistenceContext
    private EntityManager entityManager;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    public WalletResponse createWallet(WalletRequest request) {

        LocalDateTime now = LocalDateTime.now();

        Wallet wallet = new Wallet();
        wallet.setOwnerName(request.ownerName());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency(request.currency());
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);

        Wallet savedWallet = walletRepository.save(wallet);

        return new WalletResponse(
                savedWallet.getId(),
                savedWallet.getOwnerName(),
                savedWallet.getBalance(),
                savedWallet.getCurrency(),
                savedWallet.getCreatedAt(),
                savedWallet.getUpdatedAt()
        );
    }

    public WalletResponse getWallet(Long id) {

        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(id));

        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerName(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

    @Transactional
    public WalletResponse deposit(Long walletId, MoneyRequest request) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        wallet.setBalance(
                wallet.getBalance().add(request.amount())
        );

        wallet.setUpdatedAt(LocalDateTime.now());

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(WalletTransactionType.DEPOSIT);
        transaction.setAmount(request.amount());
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);

        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerName(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }


    @Transactional
    public WalletResponse withdraw(Long walletId, MoneyRequest request) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    wallet.getBalance(),
                    request.amount()
            );
        }

        wallet.setBalance(
                wallet.getBalance().subtract(request.amount())
        );

        wallet.setUpdatedAt(LocalDateTime.now());

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setType(WalletTransactionType.WITHDRAWAL);
        transaction.setAmount(request.amount());
        transaction.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(transaction);

        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerName(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }


//    @Transactional
//    public WalletResponse transfer(Long fromWalletId, TransferRequest request) {
//
//        Wallet fromWallet = walletRepository.findById(fromWalletId)
//                .orElseThrow(() -> new WalletNotFoundException(fromWalletId));
//
//        Wallet toWallet = walletRepository.findById(request.toWalletId())
//                .orElseThrow(() -> new WalletNotFoundException(request.toWalletId()));
//
//        if (fromWalletId.equals(request.toWalletId())) {
//            throw new IllegalArgumentException("Source and destination wallets must be different");
//        }
//
//        if (fromWallet.getBalance().compareTo(request.amount()) < 0) {
//            throw new InsufficientBalanceException(
//                    fromWallet.getBalance(),
//                    request.amount()
//            );
//        }
//
//        // Debit source wallet
//        fromWallet.setBalance(
//                fromWallet.getBalance().subtract(request.amount())
//        );
//        fromWallet.setUpdatedAt(LocalDateTime.now());
//
//        // Credit destination wallet
//        toWallet.setBalance(
//                toWallet.getBalance().add(request.amount())
//        );
//        toWallet.setUpdatedAt(LocalDateTime.now());
//
//        // Withdrawal transaction
//        WalletTransaction withdrawal = new WalletTransaction();
//        withdrawal.setWallet(fromWallet);
//        withdrawal.setType(WalletTransactionType.WITHDRAWAL);
//        withdrawal.setAmount(request.amount());
//        withdrawal.setCreatedAt(LocalDateTime.now());
//
//        walletTransactionRepository.save(withdrawal);
//
//        // Deposit transaction
//        WalletTransaction deposit = new WalletTransaction();
//        deposit.setWallet(toWallet);
//        deposit.setType(WalletTransactionType.DEPOSIT);
//        deposit.setAmount(request.amount());
//        deposit.setCreatedAt(LocalDateTime.now());
//
//        walletTransactionRepository.save(deposit);
//
//        return toResponse(fromWallet);
//    }


    @Transactional
    public WalletResponse transfer(
            Long fromWalletId,
            TransferRequest request
    ) {

        Long toWalletId = request.toWalletId();

        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException(
                    "Source and destination wallets must be different"
            );
        }

        // Always acquire wallet locks in a consistent order
        Long firstWalletId = Math.min(fromWalletId, toWalletId);
        Long secondWalletId = Math.max(fromWalletId, toWalletId);

        Wallet firstWallet = walletRepository
                .findByIdForUpdate(firstWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(firstWalletId));

        Wallet secondWallet = walletRepository
                .findByIdForUpdate(secondWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(secondWalletId));

        // Map the locked wallets back to transfer direction
        Wallet fromWallet;
        Wallet toWallet;

        if (fromWalletId.equals(firstWalletId)) {
            fromWallet = firstWallet;
            toWallet = secondWallet;
        } else {
            fromWallet = secondWallet;
            toWallet = firstWallet;
        }

        // Validate balance AFTER acquiring the lock
        if (fromWallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    fromWallet.getBalance(),
                    request.amount()
            );
        }

        // Debit source wallet
        fromWallet.setBalance(
                fromWallet.getBalance()
                        .subtract(request.amount())
        );

        fromWallet.setUpdatedAt(LocalDateTime.now());

        // Credit destination wallet
        toWallet.setBalance(
                toWallet.getBalance()
                        .add(request.amount())
        );

        toWallet.setUpdatedAt(LocalDateTime.now());

        // Withdrawal transaction
        WalletTransaction withdrawal = new WalletTransaction();

        withdrawal.setWallet(fromWallet);
        withdrawal.setType(WalletTransactionType.WITHDRAWAL);
        withdrawal.setAmount(request.amount());
        withdrawal.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(withdrawal);

        // Deposit transaction
        WalletTransaction deposit = new WalletTransaction();

        deposit.setWallet(toWallet);
        deposit.setType(WalletTransactionType.DEPOSIT);
        deposit.setAmount(request.amount());
        deposit.setCreatedAt(LocalDateTime.now());

        walletTransactionRepository.save(deposit);

        return toResponse(fromWallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerName(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }


    @Transactional(readOnly = true)
    public void testLazy(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));

        System.out.println("Wallet loaded");

        System.out.println("Transaction count = "
                + wallet.getTransactions().size());
    }


    @Transactional(readOnly = true)
    public void testTransactionWallet(Long transactionId) {

        WalletTransaction transaction = walletTransactionRepository
                .findById(transactionId)
                .orElseThrow();

        System.out.println("Transaction loaded");

        System.out.println("Wallet owner = "
                + transaction.getWallet().getOwnerName());
    }


    @Transactional(readOnly = true)
    public void testNPlusOne() {

        List<Wallet> wallets = walletRepository.findAll();

        System.out.println("Wallets loaded = " + wallets.size());

        for (Wallet wallet : wallets) {
            System.out.println(
                    "Wallet " + wallet.getId()
                            + " transaction count = "
                            + wallet.getTransactions().size()
            );
        }
    }



    @Transactional(readOnly = true)
    public void testEntityGraph() {

        List<Wallet> wallets =
                walletRepository.findAllWithTransactionsUsingEntityGraph();

        System.out.println("Wallets loaded = " + wallets.size());

        for (Wallet wallet : wallets) {
            System.out.println(
                    "Wallet " + wallet.getId()
                            + " transaction count = "
                            + wallet.getTransactions().size()
            );
        }
    }



    @Transactional(readOnly = true)
    public void testPersistenceContext() {

        Wallet wallet1 = walletRepository.findById(1L)
                .orElseThrow();

        System.out.println("First load: " + wallet1.getOwnerName());

        Wallet wallet2 = walletRepository.findById(1L)
                .orElseThrow();

        System.out.println("Second load: " + wallet2.getOwnerName());

        System.out.println("Same object = " + (wallet1 == wallet2));
    }


//    @Transactional
//    public void testDirtyChecking() {
//
//        Wallet wallet = walletRepository.findById(1L)
//                .orElseThrow();
//
//        System.out.println("Original balance = " + wallet.getBalance());
//
//        wallet.setBalance(wallet.getBalance().add(new BigDecimal("10.00")));
//
//        System.out.println("New balance = " + wallet.getBalance());
//
//        // Intentionally NO walletRepository.save(wallet)
//    }


    @Transactional
    public void testDirtyChecking() {

        Wallet wallet = walletRepository.findById(1L)
                .orElseThrow();

        System.out.println("Original balance = " + wallet.getBalance());

        wallet.setBalance(
                wallet.getBalance().add(new BigDecimal("10.00"))
        );

        System.out.println("New balance = " + wallet.getBalance());

        System.out.println("Before flush");

        entityManager.flush();

        System.out.println("After flush");

        // Intentionally NO walletRepository.save(wallet)
    }


//    @Transactional(isolation = Isolation.REPEATABLE_READ)
//    public void testRepeatableRead(Long walletId) {
//
//        Wallet wallet1 = walletRepository.findById(walletId)
//                .orElseThrow();
//
//        System.out.println("First read balance = " + wallet1.getBalance());
//
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        entityManager.clear();
//
//        Wallet wallet2 = walletRepository.findById(walletId)
//                .orElseThrow();
//
//        System.out.println("Second read balance = " + wallet2.getBalance());
//    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void testRepeatableRead(Long walletId) {

        Wallet wallet1 = walletRepository.findById(walletId)
                .orElseThrow();

        System.out.println("First read balance = " + wallet1.getBalance());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        entityManager.clear();

        Wallet wallet2 = walletRepository.findById(walletId)
                .orElseThrow();

        System.out.println("Second read balance = " + wallet2.getBalance());
    }


    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void testDirtyRead(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow();

        System.out.println("Read balance = " + wallet.getBalance());
    }


    @Transactional
    public void testUncommittedUpdate(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow();

        System.out.println("Original balance = " + wallet.getBalance());

        wallet.setBalance(new BigDecimal("666.0000"));
        entityManager.flush();

        System.out.println("Changed balance = " + wallet.getBalance());
        System.out.println("Sleeping before commit...");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Transaction B finishing...");
        throw new RuntimeException("Intentional rollback for dirty-read test");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void testSerializable(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow();

        System.out.println("Transaction started");
        System.out.println("Balance = " + wallet.getBalance());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Transaction finishing");
    }



}