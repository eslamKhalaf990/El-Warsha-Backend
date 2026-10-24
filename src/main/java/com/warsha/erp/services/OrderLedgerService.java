package com.warsha.erp.services;

import com.warsha.erp.dtos.BankTransactionDTO;
import com.warsha.erp.dtos.CreatePaymentRequest;
import com.warsha.erp.entities.BankTransaction;
import com.warsha.erp.entities.Order;
import com.warsha.erp.repository.BankTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps an order's money in the bank ledger consistent with the order.
 *
 * Every bank transaction for an order is tagged ReferenceType "Order" + the order id.
 * Nothing is ever deleted: corrections are posted as reversing Withdrawals, so the
 * history stays auditable and {@link #reverseAll} is safe to call more than once.
 */
@Service
public class OrderLedgerService {

    static final String REFERENCE_TYPE = "Order";
    static final String DEPOSIT = "Deposit";
    static final String WITHDRAWAL = "Withdrawal";
    static final long ORDER_INCOME_CATEGORY_ID = 1L;
    static final long DEFAULT_BANK_ACCOUNT_ID = 1L;

    /** Bank account 3 charges a fixed fee on incoming transfers. */
    static final long FEE_BANK_ACCOUNT_ID = 3L;
    static final BigDecimal TRANSFER_FEE = new BigDecimal("5.00");

    private final BankTransactionService bankTransactionService;
    private final BankTransactionRepository bankTransactionRepository;
    private final PaymentService paymentService;

    public OrderLedgerService(BankTransactionService bankTransactionService,
                              BankTransactionRepository bankTransactionRepository,
                              PaymentService paymentService) {
        this.bankTransactionService = bankTransactionService;
        this.bankTransactionRepository = bankTransactionRepository;
        this.paymentService = paymentService;
    }

    /**
     * Records the down payment for a new order: a payment record (which also stores the
     * payment method) and, when money was received, a bank deposit.
     */
    public void recordDownPayment(Order order, BigDecimal amount, String paymentMethod, Long bankAccountId) {
        CreatePaymentRequest payment = new CreatePaymentRequest();
        payment.setOrderId(order.getId());
        payment.setAmountPaid(amount.doubleValue());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus("Completed");
        paymentService.createPayment(payment);

        deposit(order, amount, bankAccountId, "Down payment for order #" + order.getId());
    }

    /**
     * Replaces the down payment of an edited order. If the money in the bank already
     * matches, the ledger is left untouched; otherwise the old entries are reversed
     * and the new down payment is deposited.
     */
    public void replaceDownPayment(Order order, BigDecimal amount, String paymentMethod, Long bankAccountId) {
        long accountId = bankAccountId != null ? bankAccountId : DEFAULT_BANK_ACCOUNT_ID;
        Map<Long, BigDecimal> expected = new TreeMap<>();
        BigDecimal net = afterFee(amount, accountId);
        if (net.signum() > 0) expected.put(accountId, net);

        paymentService.deletePaymentsByOrderId(order.getId());
        CreatePaymentRequest payment = new CreatePaymentRequest();
        payment.setOrderId(order.getId());
        payment.setAmountPaid(amount.doubleValue());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus("Completed");
        paymentService.createPayment(payment);

        if (expected.equals(netByAccount(order.getId()))) return;
        reverseAll(order, "order edited");
        deposit(order, amount, accountId, "Down payment for order #" + order.getId() + " (edited)");
    }

    /** Deposits what is still due when the order is completed. */
    public void recordFinalPayment(Order order, Long bankAccountId) {
        BigDecimal due = BigDecimal.valueOf(order.getTotalPrice()).setScale(2, RoundingMode.HALF_UP);
        deposit(order, due, bankAccountId, "Order #" + order.getId() + " Final Payment");
    }

    /**
     * Withdraws whatever the order still holds in each bank account, bringing its
     * net balance to zero. Calling it again does nothing.
     */
    public void reverseAll(Order order, String reason) {
        netByAccount(order.getId()).forEach((accountId, net) -> {
            if (net.signum() > 0) {
                post(order, accountId, WITHDRAWAL, net,
                        "Reversal for order #" + order.getId() + " (" + reason + ")");
            }
        });
    }

    /** Net amount (deposits − withdrawals) the order holds per bank account. */
    Map<Long, BigDecimal> netByAccount(Long orderId) {
        Map<Long, BigDecimal> net = new TreeMap<>();
        for (BankTransaction tx : bankTransactionRepository.findByReferenceTypeAndReferenceId(REFERENCE_TYPE, orderId)) {
            BigDecimal signed = DEPOSIT.equals(tx.getTransactionType()) ? tx.getAmount() : tx.getAmount().negate();
            net.merge(tx.getBankAccount().getId(), signed, BigDecimal::add);
        }
        net.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        net.values().removeIf(v -> v.signum() == 0);
        return net;
    }

    private void deposit(Order order, BigDecimal amount, Long bankAccountId, String description) {
        long accountId = bankAccountId != null ? bankAccountId : DEFAULT_BANK_ACCOUNT_ID;
        BigDecimal net = afterFee(amount, accountId);
        if (net.signum() <= 0) return; // nothing received, nothing to record
        post(order, accountId, DEPOSIT, net, description);
    }

    private static BigDecimal afterFee(BigDecimal amount, long accountId) {
        BigDecimal net = accountId == FEE_BANK_ACCOUNT_ID ? amount.subtract(TRANSFER_FEE) : amount;
        return net.setScale(2, RoundingMode.HALF_UP);
    }

    private void post(Order order, long accountId, String type, BigDecimal amount, String description) {
        BankTransactionDTO tx = new BankTransactionDTO();
        tx.setBankAccountId(accountId);
        tx.setTransactionType(type);
        tx.setReferenceType(REFERENCE_TYPE);
        tx.setReferenceId(order.getId());
        tx.setCategoryId(ORDER_INCOME_CATEGORY_ID);
        tx.setDescription(description);
        tx.setAmount(amount);
        bankTransactionService.createTransaction(tx);
    }
}
