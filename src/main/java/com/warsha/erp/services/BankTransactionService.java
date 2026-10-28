package com.warsha.erp.services;

import com.warsha.erp.dtos.ManualTransactionRequest;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.ErrorCode;
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

    /**
     * A deposit or withdrawal entered by staff. Unlike order bookings, a
     * withdrawal can't take an account below zero.
     */
    @Transactional
    public BankTransaction createManual(ManualTransactionRequest request) {
        BankAccount account = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new ValidationException("bankAccountId", "Choose a bank account from the list."));
        if ("Withdrawal".equals(request.getTransactionType())) {
            BigDecimal balance = account.getCurrentBalance() == null ? BigDecimal.ZERO : account.getCurrentBalance();
            if (request.getAmount().compareTo(balance) > 0) {
                throw new BusinessRuleException(account.getName() + " only has " + balance.stripTrailingZeros().toPlainString()
                        + " EGP, so " + request.getAmount().stripTrailingZeros().toPlainString() + " EGP can't be withdrawn.");
            }
        }
        BankTransactionDTO dto = new BankTransactionDTO();
        dto.setBankAccountId(request.getBankAccountId());
        dto.setCategoryId(request.getCategoryId());
        dto.setTransactionType(request.getTransactionType());
        dto.setAmount(request.getAmount());
        String description = request.getDescription();
        dto.setDescription(description == null || description.isBlank() ? null : description.trim());
        dto.setReferenceType("Manual");
        return createTransaction(dto);
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

    /**
     * Regular accounts, plus the owner's safe when [ownerPassword] matches.
     * The first password ever sent becomes the safe's password. A wrong
     * password is refused (403) so the screen can say so.
     */
    public List<BankAccountDTO> getAllAccounts(String ownerPassword) {
        boolean asked = ownerPassword != null && !ownerPassword.isBlank();
        if (asked && unlocksOwnerSafe(ownerPassword)) {
            return bankAccountRepository.findAllAccounts();
        }
        if (asked) {
            throw new AppException(ErrorCode.FORBIDDEN, "Wrong owner password.");
        }
        return bankAccountRepository.findAvailableAccounts();
    }

    /** True when [password] opens the owner's safe (setting it on first use). */
    private boolean unlocksOwnerSafe(String password) {
        Optional<BankAccount> safe = bankAccountRepository.findFirstByIsOwnerSafeTrue();
        if (safe.isEmpty()) return false;
        BankAccount account = safe.get();
        if (account.getHashedPassword() == null) {
            account.setHashedPassword(passwordEncoder.encode(password));
            bankAccountRepository.save(account);
            return true;
        }
        return passwordEncoder.matches(password, account.getHashedPassword());
    }

    /**
     * Deletes every bank transaction and zeroes every balance. Once the owner's
     * safe has a password, the reset needs it.
     */
    @Transactional
    public void resetAll(String ownerPassword) {
        boolean protectedReset = bankAccountRepository.findFirstByIsOwnerSafeTrue()
                .map(a -> a.getHashedPassword() != null).orElse(false);
        if (protectedReset && (ownerPassword == null || !unlocksOwnerSafe(ownerPassword))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Enter the owner password to reset all transactions.");
        }
        hardResetSystem();
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
