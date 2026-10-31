package com.warsha.erp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.warsha.erp.dtos.ManualTransactionRequest;
import com.warsha.erp.entities.BankAccount;
import com.warsha.erp.entities.BankTransaction;
import com.warsha.erp.exceptions.AppException;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.ErrorCode;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.BankAccountRepository;
import com.warsha.erp.repository.BankTransactionRepository;
import com.warsha.erp.repository.TransactionCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BankTransactionServiceTest {

    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final BankAccountRepository accounts = mock(BankAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final BankTransactionService service = new BankTransactionService();
    private BankAccount cash;
    private BankAccount safe;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "transactionRepository", transactions);
        ReflectionTestUtils.setField(service, "bankAccountRepository", accounts);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "categoryRepository", mock(TransactionCategoryRepository.class));
        cash = new BankAccount();
        cash.setId(1L);
        cash.setName("Vodafone Cash");
        cash.setCurrentBalance(BigDecimal.valueOf(500));
        safe = new BankAccount();
        safe.setId(9L);
        safe.setName("Safe");
        when(accounts.findById(1L)).thenReturn(Optional.of(cash));
        when(accounts.findFirstByIsOwnerSafeTrue()).thenReturn(Optional.of(safe));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactions.getBalanceByAccount(1L)).thenReturn(BigDecimal.valueOf(400));
        when(accounts.findAllAccounts()).thenReturn(List.of());
        when(accounts.findAvailableAccounts()).thenReturn(List.of());
    }

    private static ManualTransactionRequest request(String type, double amount) {
        ManualTransactionRequest r = new ManualTransactionRequest();
        r.setBankAccountId(1L);
        r.setTransactionType(type);
        r.setAmount(BigDecimal.valueOf(amount));
        r.setDescription("  ");
        return r;
    }

    @Test
    void aWithdrawalCannotTakeAnAccountBelowZero() {
        assertThatThrownBy(() -> service.createManual(request("Withdrawal", 600)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Vodafone Cash only has 500 EGP, so 600 EGP can't be withdrawn.");
        verify(transactions, never()).save(any());
    }

    @Test
    void aManualEntryIsBookedAndTheBalanceUpdated() {
        BankTransaction saved = service.createManual(request("Withdrawal", 100));

        assertThat(saved.getReferenceType()).isEqualTo("Manual");
        assertThat(saved.getDescription()).isNull();
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("400");
    }

    @Test
    void anUnknownAccountIsAFieldError() {
        ManualTransactionRequest r = request("Deposit", 10);
        r.setBankAccountId(5L);
        assertThatThrownBy(() -> service.createManual(r)).isInstanceOf(ValidationException.class);
    }

    @Test
    void theFirstOwnerPasswordIsSetThenChecked() {
        service.getAllAccounts("open-sesame");
        assertThat(safe.getHashedPassword()).isNotNull();
        verify(accounts).findAllAccounts();

        assertThatThrownBy(() -> service.getAllAccounts("wrong"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));

        service.getAllAccounts(null);
        verify(accounts).findAvailableAccounts();
    }

    @Test
    void resetNeedsTheOwnerPasswordOnceOneIsSet() {
        safe.setHashedPassword(encoder.encode("open-sesame"));

        assertThatThrownBy(() -> service.resetAll(null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.resetAll("wrong")).isInstanceOf(AppException.class);
        verify(transactions, never()).deleteAllInBatch();

        service.resetAll("open-sesame");
        verify(transactions).deleteAllInBatch();
        verify(accounts).resetAllBalances();
    }

    @Test
    void thePasswordHashNeverReachesJson() throws Exception {
        safe.setHashedPassword("$2a$secret-hash");
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(safe);
        assertThat(json).doesNotContain("secret-hash").doesNotContain("hashedPassword");
    }
}
