package com.warsha.erp.services;

import com.warsha.erp.dtos.BankTransactionDTO;
import com.warsha.erp.entities.BankAccount;
import com.warsha.erp.entities.BankTransaction;
import com.warsha.erp.entities.Order;
import com.warsha.erp.repository.BankTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Runs the ledger against an in-memory list of bank transactions. */
class OrderLedgerServiceTest {

    private final List<BankTransaction> bank = new ArrayList<>();
    private final BankTransactionService bankService = mock(BankTransactionService.class);
    private final BankTransactionRepository bankRepo = mock(BankTransactionRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderLedgerService ledger = new OrderLedgerService(bankService, bankRepo, paymentService);

    private final Order order = new Order();

    @BeforeEach
    void setUp() {
        order.setId(12L);
        order.setTotalPrice(300.0);
        when(bankService.createTransaction(any())).thenAnswer(inv -> {
            BankTransactionDTO dto = inv.getArgument(0);
            BankTransaction tx = new BankTransaction();
            BankAccount account = new BankAccount();
            account.setId(dto.getBankAccountId());
            tx.setBankAccount(account);
            tx.setTransactionType(dto.getTransactionType());
            tx.setAmount(dto.getAmount());
            tx.setReferenceType(dto.getReferenceType());
            tx.setReferenceId(dto.getReferenceId());
            tx.setDescription(dto.getDescription());
            bank.add(tx);
            return tx;
        });
        when(bankRepo.findByReferenceTypeAndReferenceId(eq("Order"), anyLong()))
                .thenAnswer(inv -> List.copyOf(bank));
    }

    private Map<Long, BigDecimal> held() {
        return ledger.netByAccount(12L);
    }

    @Test
    void downPaymentIsDepositedAndRecorded() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 2L);

        assertThat(held()).containsExactlyEntriesOf(Map.of(2L, new BigDecimal("200.00")));
        verify(paymentService).createPayment(argThat(p -> p.getAmountPaid() == 200.0 && "Cash".equals(p.getPaymentMethod())));
    }

    @Test
    void noBankEntryWhenNothingWasPaid() {
        ledger.recordDownPayment(order, BigDecimal.ZERO, "Cash on delivery", null);

        assertThat(bank).isEmpty();
        verify(paymentService).createPayment(any()); // still stores the payment method
    }

    @Test
    void feeAccountKeepsItsTransferFee() {
        ledger.recordDownPayment(order, new BigDecimal("100"), "InstaPay", 3L);
        assertThat(held()).containsExactlyEntriesOf(Map.of(3L, new BigDecimal("95.00")));
    }

    @Test
    void editingDoesNotDepositTheDownPaymentAgain() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 1L);

        ledger.replaceDownPayment(order, new BigDecimal("200"), "Cash", 1L);
        ledger.replaceDownPayment(order, new BigDecimal("200"), "Cash", 1L);

        assertThat(bank).hasSize(1);
        assertThat(held()).containsExactlyEntriesOf(Map.of(1L, new BigDecimal("200.00")));
    }

    @Test
    void editingTheAmountReversesThenDepositsTheNewOne() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 1L);

        ledger.replaceDownPayment(order, new BigDecimal("250"), "Wallet", 2L);

        assertThat(held()).containsExactlyEntriesOf(Map.of(2L, new BigDecimal("250.00")));
        assertThat(bank).extracting(BankTransaction::getTransactionType)
                .containsExactly("Deposit", "Withdrawal", "Deposit");
    }

    @Test
    void editingWithoutABankAccountKeepsTheMoneyWhereItIs() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Wallet", 2L);

        ledger.replaceDownPayment(order, new BigDecimal("200"), "Wallet", null);

        assertThat(bank).hasSize(1);
        assertThat(held()).containsExactlyEntriesOf(Map.of(2L, new BigDecimal("200.00")));
    }

    @Test
    void editingTheAmountWithoutABankAccountStaysInTheSameAccount() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Wallet", 2L);

        ledger.replaceDownPayment(order, new BigDecimal("300"), "Wallet", null);

        assertThat(held()).containsExactlyEntriesOf(Map.of(2L, new BigDecimal("300.00")));
    }

    @Test
    void cancellingRefundsEverythingOnce() {
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 1L);

        ledger.reverseAll(order, "order cancelled");
        ledger.reverseAll(order, "order deleted");

        assertThat(held()).isEmpty();
        assertThat(bank).hasSize(2);
        assertThat(bank.get(1).getDescription()).isEqualTo("Reversal for order #12 (order cancelled)");
    }

    @Test
    void reversalAlsoCleansUpOldDuplicateDeposits() {
        // What the old code did on every edit: deposit the down payment again.
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 1L);
        ledger.recordDownPayment(order, new BigDecimal("200"), "Cash", 1L);

        ledger.reverseAll(order, "order cancelled");

        assertThat(held()).isEmpty();
        assertThat(bank.get(2).getAmount()).isEqualByComparingTo("400");
    }

    @Test
    void finalPaymentDepositsWhatIsStillDue() {
        ledger.recordFinalPayment(order, 2L);
        assertThat(held()).containsExactlyEntriesOf(Map.of(2L, new BigDecimal("300.00")));
    }

    @Test
    void fullyPrepaidOrderHasNoFinalDeposit() {
        order.setTotalPrice(0.0);
        ledger.recordFinalPayment(order, 2L);
        assertThat(bank).isEmpty();
    }
}
