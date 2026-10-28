package com.warsha.erp.services;

import com.warsha.erp.entities.Customer;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderStatus;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceStatusTest {

    @Mock OrderRepository orderRepository;
    @Mock CustomerService customerService;
    @Mock InvoiceService invoiceService;
    @Mock EmailService emailService;
    @Mock PaymentService paymentService;
    @Mock OrderLedgerService ledger;
    @Mock StockService stockService;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerService,
                invoiceService, emailService, paymentService, ledger, stockService, mock(ShippingZoneService.class));
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(12L);
        order.setStatus(status);
        order.setTotalPrice(450.0);
        order.setItems(new ArrayList<>());
        Customer customer = new Customer();
        customer.setEmail("c@mail.com");
        customer.setFullName("Mona");
        order.setCustomer(customer);
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void unknownOrderIsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateOrderStatus(99L, OrderStatus.SHIPPED, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order #99 was not found.");
    }

    @Test
    void movesForward() {
        order(OrderStatus.PROCESSING);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order saved = service.updateOrderStatus(12L, OrderStatus.SHIPPED, null);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verifyNoInteractions(ledger);
    }

    @Test
    void refusesToGoBackwards() {
        order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderStatus(12L, OrderStatus.PENDING, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 can't go back from Shipped to Pending.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void completingTwiceDoesNotDepositTwice() {
        order(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.updateOrderStatus(12L, OrderStatus.COMPLETED, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is already completed.");
        verifyNoInteractions(ledger);
    }

    @Test
    void completingNeedsABankAccount() {
        order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderStatus(12L, OrderStatus.COMPLETED, null))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(ledger);
    }

    @Test
    void completingRecordsTheFinalPayment() {
        order(OrderStatus.SHIPPED);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order saved = service.updateOrderStatus(12L, OrderStatus.COMPLETED, 2L);

        verify(ledger).recordFinalPayment(saved, 2L);
    }

    @Test
    void completedOrdersCannotBeCancelled() {
        order(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> service.cancelOrder(12L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is completed and its status can no longer be changed.");
        verifyNoInteractions(paymentService, emailService);
    }

    @Test
    void cancellingTwiceIsRejectedInsteadOfRestockingAgain() {
        order(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancelOrder(12L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is already cancelled.");
        verifyNoInteractions(stockService);
    }

    @Test
    void statusEndpointRoutesCancelThroughCancelLogic() {
        order(OrderStatus.PENDING);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order saved = service.updateOrderStatus(12L, OrderStatus.CANCELLED, null);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(paymentService).cancelPaymentsByOrderId(12L);
        verify(ledger).reverseAll(saved, "order cancelled");
    }

    @Test
    void listRejectsReversedDateRange() {
        assertThatThrownBy(() -> service.listOrders(java.time.LocalDate.of(2026, 5, 10), java.time.LocalDate.of(2026, 5, 1), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("The end date can't be before the start date.");
    }

    @Test
    void listRejectsRangesLongerThanAYear() {
        assertThatThrownBy(() -> service.listOrders(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2026, 1, 2), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Choose a date range of at most one year.");
    }

    @Test
    void creatingWithoutACustomerIsAValidationError() {
        assertThatThrownBy(() -> service.createOrder(new com.warsha.erp.dtos.OrderRequest()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Choose a customer for the order.");
    }

    @Test
    void shippedOrdersCannotBeEdited() {
        order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrder(12L, new com.warsha.erp.dtos.OrderRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is shipped and can no longer be edited.");
    }
}
