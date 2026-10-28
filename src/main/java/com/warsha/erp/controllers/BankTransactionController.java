package com.warsha.erp.controllers;

import com.warsha.erp.dtos.BankAccountDTO;
import com.warsha.erp.dtos.BankSummaryDTO;
import com.warsha.erp.dtos.ManualTransactionRequest;
import com.warsha.erp.dtos.TransactionCategoryDTO;
import com.warsha.erp.entities.BankTransaction;
import com.warsha.erp.services.BankTransactionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/bank")
public class BankTransactionController {

    static final String OWNER_PASSWORD_HEADER = "X-Owner-Password";

    @Autowired
    private BankTransactionService transactionService;

    /** A deposit or withdrawal entered by staff. */
    @PostMapping("/transaction")
    public ResponseEntity<BankTransaction> addTransaction(@Valid @RequestBody ManualTransactionRequest request) {
        return ResponseEntity.ok(transactionService.createManual(request));
    }

    @GetMapping("/transactions/{accountId}")
    public ResponseEntity<List<BankTransaction>> getTransactions(@PathVariable Long accountId) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(accountId));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<BankTransaction>> getTransactions() {
        return ResponseEntity.ok(transactionService.getTransactions());
    }

    @GetMapping("/balance/{accountId}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long accountId) {
        return ResponseEntity.ok(transactionService.getAccountBalance(accountId));
    }

    @GetMapping("/summary")
    public ResponseEntity<BankSummaryDTO> getSummary() {
        BankSummaryDTO summary = transactionService.getTotalSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Regular accounts; with the owner password (header {@code X-Owner-Password},
     * or the older {@code ?password=}) the owner's safe too. A wrong password is 403.
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<BankAccountDTO>> getAllAccounts(
            @RequestHeader(name = OWNER_PASSWORD_HEADER, required = false) String headerPassword,
            @RequestParam(name = "password", required = false) String queryPassword) {
        return ResponseEntity.ok(transactionService.getAllAccounts(headerPassword != null ? headerPassword : queryPassword));
    }

    @GetMapping("/transactionCategories")
    public ResponseEntity<List<TransactionCategoryDTO>> getTransactionCategories() {
        List<TransactionCategoryDTO> accounts = transactionService.getTransactionCategory();
        return ResponseEntity.ok(accounts);
    }

    /** Deletes every transaction and zeroes balances; needs the owner password once one is set. */
    @DeleteMapping("/resetTransactions")
    public ResponseEntity<Void> resetSystem(
            @RequestHeader(name = OWNER_PASSWORD_HEADER, required = false) String ownerPassword) {
        transactionService.resetAll(ownerPassword);
        return ResponseEntity.noContent().build();
    }
}
