package com.dhruv.banking_wallet.controller;

import com.dhruv.banking_wallet.dto.MoneyRequest;
import com.dhruv.banking_wallet.dto.TransferRequest;
import com.dhruv.banking_wallet.dto.WalletRequest;
import com.dhruv.banking_wallet.dto.WalletResponse;
import com.dhruv.banking_wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @Valid @RequestBody WalletRequest request) {

        return walletService.createWallet(request);
    }

    @GetMapping("/{id}")
    public WalletResponse getWallet(@PathVariable Long id) {
        return walletService.getWallet(id);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request) {

        return walletService.deposit(id, request);
    }


    @PostMapping("/{id}/withdraw")
    public WalletResponse withdraw(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request) {

        return walletService.withdraw(id, request);
    }


    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse transfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferRequest request) {

        return walletService.transfer(id, request);
    }

    @GetMapping("/{id}/test-lazy")
    @ResponseStatus(HttpStatus.OK)
    public void testLazy(@PathVariable Long id) {
        walletService.testLazy(id);
    }


    @GetMapping("/transactions/{id}/test-eager")
    public void testEager(@PathVariable Long id) {
        walletService.testTransactionWallet(id);
    }

    @GetMapping("/test-n-plus-one")
    @ResponseStatus(HttpStatus.OK)
    public void testNPlusOne() {
        walletService.testNPlusOne();
    }


    @GetMapping("/test-entity-graph")
    @ResponseStatus(HttpStatus.OK)
    public void testEntityGraph() {
        walletService.testEntityGraph();
    }

    @GetMapping("/test-persistence-context")
    public void testPersistenceContext() {
        walletService.testPersistenceContext();
    }

    @GetMapping("/test-dirty-checking")
    public void testDirtyChecking() {
        walletService.testDirtyChecking();
    }

    @GetMapping("/test-repeatable-read/{walletId}")
    public ResponseEntity<Void> testRepeatableRead(
            @PathVariable Long walletId) {

        walletService.testRepeatableRead(walletId);

        return ResponseEntity.ok().build();
    }

   //Transaction B
    @GetMapping("/test-uncommitted-update/{walletId}")
    public ResponseEntity<Void> testUncommittedUpdate(
            @PathVariable Long walletId) {

        walletService.testUncommittedUpdate(walletId);

        return ResponseEntity.ok().build();
    }

    //Transaction A
    @GetMapping("/test-dirty-read/{walletId}")
    public ResponseEntity<Void> testDirtyRead(
            @PathVariable Long walletId) {

        walletService.testDirtyRead(walletId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/test-serializable/{walletId}")
    public ResponseEntity<Void> testSerializable(
            @PathVariable Long walletId) {

        walletService.testSerializable(walletId);

        return ResponseEntity.ok().build();
    }
}