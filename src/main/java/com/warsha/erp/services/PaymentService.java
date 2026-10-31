package com.warsha.erp.services;

import com.warsha.erp.dtos.CreatePaymentRequest;
import com.warsha.erp.dtos.PaymentDto;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.Payment;
import com.warsha.erp.repository.OrderRepository;
import com.warsha.erp.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public PaymentService(PaymentRepository paymentRepo, OrderRepository orderRepo) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    public PaymentDto createPayment(CreatePaymentRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Creating payment for Order ID: " + request.getOrderId());

        Order order = orderRepo.findById(request.getOrderId())
                .orElseThrow(() -> {
                    System.out.println("[" + getTimestamp() + "] ERROR: Order not found for ID: " + request.getOrderId());
                    return new RuntimeException("Order not found");
                });

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmountPaid(request.getAmountPaid());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentStatus(request.getPaymentStatus() != null ? request.getPaymentStatus() : "Unpaid");
        payment.setPaymentDate(LocalDateTime.now());

        paymentRepo.save(payment);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Payment record saved (Amount: " + payment.getAmountPaid() + ")");

        return new PaymentDto(payment);
    }

    public List<PaymentDto> getAllPayments() {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching all payment records");
        return paymentRepo.findAll().stream()
                .map(PaymentDto::new)
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getPaymentsByOrder(Long orderId) {
        return paymentRepo.findByOrderId(orderId).stream()
                .map(PaymentDto::new)
                .collect(Collectors.toList());
    }

    /** Payment method of each order (from its first payment), in one query. */
    public Map<Long, String> getPaymentMethodsByOrder(Collection<Long> orderIds) {
        Map<Long, String> methods = new HashMap<>();
        if (orderIds.isEmpty()) return methods;
        paymentRepo.findByOrderIdIn(orderIds).stream()
                .sorted(Comparator.comparing(Payment::getId))
                .forEach(p -> methods.putIfAbsent(p.getOrder().getId(), p.getPaymentMethod()));
        return methods;
    }

    public void deletePaymentsByOrderId(Long orderId) {
        System.out.println("[" + getTimestamp() + "] WARN: Deleting all payments for Order ID: " + orderId);
        List<Payment> payments = paymentRepo.findByOrderId(orderId);

        if (payments != null && !payments.isEmpty()) {
            paymentRepo.deleteAll(payments);
            System.out.println("[" + getTimestamp() + "] SUCCESS: Deleted " + payments.size() + " payment records.");
        } else {
            System.out.println("[" + getTimestamp() + "] INFO: No payments found to delete for Order ID: " + orderId);
        }
    }

    public void cancelPaymentsByOrderId(Long orderId) {
        System.out.println("[" + getTimestamp() + "] WARN: Cancelling payments for Order ID: " + orderId);

        List<Payment> payments = paymentRepo.findByOrderId(orderId);

        for (Payment payment : payments) {
            System.out.println("[" + getTimestamp() + "] INFO: Marking Payment ID " + payment.getId() + " as Cancelled");
            payment.setPaymentStatus("Cancelled");
            paymentRepo.save(payment);
        }

        System.out.println("[" + getTimestamp() + "] SUCCESS: All payments for Order #" + orderId + " updated to Cancelled.");
    }
}