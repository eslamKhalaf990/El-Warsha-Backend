package com.warsha.erp.services;

import com.warsha.erp.dtos.BankAccountDTO;
import com.warsha.erp.dtos.BankSummaryDTO;
import com.warsha.erp.dtos.BankTransactionDTO;
import com.warsha.erp.dtos.TransactionCategoryDTO;
import com.warsha.erp.entities.BankAccount;
import com.warsha.erp.entities.BankTransaction;
import com.warsha.erp.entities.TransactionCategory;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.BankAccountRepository;
import com.warsha.erp.repository.BankTransactionRepository;
import com.warsha.erp.repository.TransactionCategoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BankTransactionService {

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired private PasswordEncoder passwordEncoder;


    @Autowired
    private TransactionCategoryRepository categoryRepository;

    public BankTransaction createTransaction(BankTransactionDTO dto) {
        BankTransaction transaction = new BankTransaction();
        BankAccount account = bankAccountRepository.findById(dto.getBankAccountId())
                .orElseThrow(() -> new ValidationException("bankAccountId",
                        "Bank account #" + dto.getBankAccountId() + " doesn't exist."));
        transaction.setBankAccount(account);

        if (dto.getCategoryId() != null) {
            TransactionCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ValidationException("categoryId",
                            "Transaction category #" + dto.getCategoryId() + " doesn't exist."));
            transaction.setCategory(category);
        }

        transaction.setTransactionType(dto.getTransactionType());
        transaction.setAmount(dto.getAmount());
        transaction.setDescription(dto.getDescription());
        transaction.setReferenceType(dto.getReferenceType());
        transaction.setReferenceId(dto.getReferenceId());

        BankTransaction saved = transactionRepository.save(transaction);

        // Update account balance
        BigDecimal newBalance = transactionRepository.getBalanceByAccount(account.getId());
        account.setCurrentBalance(newBalance);
        bankAccountRepository.save(account);

        return saved;
    }

    public List<BankTransaction> getTransactionsByAccount(Long accountId) {
        return transactionRepository.findByBankAccountIdOrderByCreatedAtDesc(accountId);
    }

    public List<BankTransaction> getTransactions() {
        return transactionRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public BigDecimal getAccountBalance(Long accountId) {
        return transactionRepository.getBalanceByAccount(accountId);
    }

    public BankSummaryDTO getTotalSummary() {
        return transactionRepository.getTotalSummary().stream().map(row -> new BankSummaryDTO((BigDecimal) row[0], (BigDecimal) row[1], (BigDecimal) row[2])).toList().getFirst();
    }

    public List<TransactionCategoryDTO> getTransactionCategory() {
        return bankAccountRepository.findAllTransactionCategories().stream().map(
                row -> new TransactionCategoryDTO(row.getCategoryID(),
                        row.getCategoryName(),
                        row.getCategoryType())).
                toList();
    }

    public List<BankAccountDTO> getAllAccounts(String passwordForOwnerSafe) {
        // 1. Try to find the Safe Account in the DB
        Optional<BankAccount> safeAccountOpt = bankAccountRepository.findFirstByIsOwnerSafeTrue();

        if (safeAccountOpt.isPresent()) {
            BankAccount safeAccount = safeAccountOpt.get();
            String storedHash = safeAccount.getHashedPassword();

            // -----------------------------------------------------------
            // SCENARIO 1: Password is NOT set in DB yet (First Time Setup)
            // -----------------------------------------------------------
            if (storedHash == null) {
                // Only set it if the user actually sent a password (not null/empty)
                if (passwordForOwnerSafe != null && !passwordForOwnerSafe.isBlank()) {

                    safeAccount.setHashedPassword(passwordEncoder.encode(passwordForOwnerSafe));
                    bankAccountRepository.save(safeAccount);

                    // UX IMPROVEMENT: Return all accounts immediately so the user confirms it worked
                    return bankAccountRepository.findAllAccounts();
                }
            }

            // -----------------------------------------------------------
            // SCENARIO 2: Password IS set in DB (Regular Check)
            // -----------------------------------------------------------
            else {
                if (passwordForOwnerSafe != null &&
                        passwordEncoder.matches(passwordForOwnerSafe, storedHash)) {

                    // AUTHENTICATED: Return ALL accounts
                    return bankAccountRepository.findAllAccounts();
                }
            }
        }

        // Default: Show only regular accounts
        return bankAccountRepository.findAvailableAccounts();
    }

    @Transactional
    public void hardResetSystem() {
        // 1. Corresponds to: DELETE FROM [BankTransactions];
        // deleteAllInBatch() is efficient because it generates a single SQL DELETE statement.
        transactionRepository.deleteAllInBatch();

        // 2. Corresponds to: UPDATE [BankAccounts] SET CurrentBalance = 0;
        bankAccountRepository.resetAllBalances();
    }

}
