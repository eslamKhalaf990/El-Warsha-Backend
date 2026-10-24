package com.warsha.erp.services;

import com.warsha.erp.dtos.BankTransactionDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    @Mock BankTransactionService bankTransactionService;
    @Mock StockService stockService;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerService,
                invoiceService, emailService, paymentService, bankTransactionService, stockService);
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
        verifyNoInteractions(bankTransactionService);
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
        verifyNoInteractions(bankTransactionService);
    }

    @Test
    void completingNeedsABankAccount() {
        order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrderStatus(12L, OrderStatus.COMPLETED, null))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(bankTransactionService);
    }

    @Test
    void completingRecordsTheFinalPayment() {
        order(OrderStatus.SHIPPED);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateOrderStatus(12L, OrderStatus.COMPLETED, 2L);

        ArgumentCaptor<BankTransactionDTO> tx = ArgumentCaptor.forClass(BankTransactionDTO.class);
        verify(bankTransactionService).createTransaction(tx.capture());
        assertThat(tx.getValue().getBankAccountId()).isEqualTo(2L);
        assertThat(tx.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(450.0));
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
    }

    @Test
    void shippedOrdersCannotBeEdited() {
        order(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateOrder(12L, new com.warsha.erp.dtos.CreateOrderRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is shipped and can no longer be edited.");
    }
}
