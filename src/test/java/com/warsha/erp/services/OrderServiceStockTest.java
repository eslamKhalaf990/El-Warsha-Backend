package com.warsha.erp.services;

import com.warsha.erp.dtos.OrderRequest;
import com.warsha.erp.dtos.OrderItemRequest;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.OrderRepository;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Order flows against the real StockService, with the database mocked out. */
class OrderServiceStockTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private OrderService service;

    private final Map<Long, Product> db = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerService, mock(InvoiceService.class),
                mock(EmailService.class), paymentService, mock(OrderLedgerService.class),
                new StockService(productRepository, variantRepository), mock(ShippingZoneService.class));

        db.put(1L, StockServiceTest.product(1, "5", 0));
        db.put(2L, StockServiceTest.product(2, "1", 0));
        when(productRepository.findAllByIdWithLock(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.stream().map(db::get).filter(Objects::nonNull).toList();
        });
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(12L);
            return o;
        });
        when(customerService.getCustomerByID(any())).thenReturn(new Customer());
    }

    private static OrderRequest request(long[][] lines) {
        OrderRequest r = new OrderRequest();
        List<OrderItemRequest> items = new ArrayList<>();
        for (long[] line : lines) {
            OrderItemRequest i = new OrderItemRequest();
            i.setProductId(line[0]);
            i.setQuantity((int) line[1]);
            i.setUnitPrice(100.0);
            items.add(i);
        }
        r.setItems(items);
        r.setCustomerId(7L);
        r.setPaymentMethod("Cash");
        return r;
    }

    private Order existing(OrderStatus status, long productId, int qty) {
        Order order = new Order();
        order.setId(12L);
        order.setStatus(status);
        order.setTotalPrice(100.0);
        order.setCustomer(new Customer());
        OrderItems item = new OrderItems();
        item.setOrder(order);
        item.setProduct(db.get(productId));
        item.setQuantity(qty);
        order.setItems(new ArrayList<>(List.of(item)));
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));
        return order;
    }

    private String stockOf(long id) {
        return db.get(id).getQuantity();
    }

    @Test
    void erpOrderTakesStock() {
        service.createOrder(request(new long[][]{{1, 2}}));
        assertThat(stockOf(1)).isEqualTo("3");
        assertThat(db.get(1L).getSold()).isEqualTo(2);
    }

    @Test
    void erpOrderCanNoLongerOversell() {
        assertThatThrownBy(() -> service.createOrder(request(new long[][]{{2, 3}})))
                .isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void duplicateLinesForTheSameProductAddUp() {
        assertThatThrownBy(() -> service.createOrder(request(new long[][]{{1, 3}, {1, 3}})))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available: 2, requested: 3");
    }

    @Test
    void emptyOrderIsRejected() {
        assertThatThrownBy(() -> service.createOrder(request(new long[][]{})))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Add at least one product to the order.");
    }

    @Test
    void missingQuantityIsRejected() {
        OrderRequest r = request(new long[][]{{1, 1}});
        r.getItems().get(0).setQuantity(null);
        assertThatThrownBy(() -> service.createOrder(r)).isInstanceOf(ValidationException.class);
    }

    @Test
    void editingSwapsStockAndKeepsSoldCorrect() {
        existing(OrderStatus.PENDING, 1, 2);
        db.get(1L).setQuantity("3");
        db.get(1L).setSold(2);

        service.updateOrder(12L, request(new long[][]{{1, 1}, {2, 1}}));

        assertThat(stockOf(1)).isEqualTo("4");   // 3 + 2 returned - 1 taken
        assertThat(db.get(1L).getSold()).isEqualTo(1);
        assertThat(stockOf(2)).isEqualTo("0");
        assertThat(db.get(2L).getSold()).isEqualTo(1);
    }

    @Test
    void editingCanReuseTheStockTheOrderAlreadyHolds() {
        existing(OrderStatus.PENDING, 2, 1);
        db.get(2L).setQuantity("0");

        service.updateOrder(12L, request(new long[][]{{2, 1}}));

        assertThat(stockOf(2)).isEqualTo("0");
    }

    @Test
    void cancelReturnsStockOnce() {
        existing(OrderStatus.PROCESSING, 1, 2);

        service.cancelOrder(12L);
        assertThat(stockOf(1)).isEqualTo("7");

        assertThatThrownBy(() -> service.cancelOrder(12L)).isInstanceOf(BusinessRuleException.class);
        assertThat(stockOf(1)).isEqualTo("7");
    }

    @Test
    void deletingAPendingOrderReturnsStock() {
        existing(OrderStatus.PENDING, 1, 2);
        service.deleteOrder(12L);
        assertThat(stockOf(1)).isEqualTo("7");
    }

    @Test
    void deletingACancelledOrderDoesNotReturnStockAgain() {
        existing(OrderStatus.CANCELLED, 1, 2);
        service.deleteOrder(12L);
        assertThat(stockOf(1)).isEqualTo("5");
    }

    @Test
    void shippedOrdersMustBeCancelledNotDeleted() {
        existing(OrderStatus.SHIPPED, 1, 2);
        assertThatThrownBy(() -> service.deleteOrder(12L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Order #12 is shipped. Cancel it instead of deleting it.");
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void completedOrdersCannotBeDeleted() {
        existing(OrderStatus.COMPLETED, 1, 2);
        assertThatThrownBy(() -> service.deleteOrder(12L))
                .hasMessage("Order #12 is completed and can't be deleted.");
    }

    @Test
    void variantOrderTakesFromTheVariantAndUsesItsPrice() {
        Product p = db.get(1L);
        ProductVariant xl = StockServiceTest.variant(10, p, "XL", "Black", 3);
        xl.setSellingPrice(150.0);
        p.setSellingPrice(100.0);
        when(variantRepository.findActiveByProductId(1L)).thenReturn(List.of(xl));
        when(variantRepository.findById(10L)).thenReturn(Optional.of(xl));

        OrderRequest r = request(new long[][]{{1, 2}});
        r.getItems().get(0).setVariantId(10L);
        r.getItems().get(0).setUnitPrice(null);
        Order order = service.createOrder(r);

        assertThat(xl.getQuantity()).isEqualTo(1);
        assertThat(stockOf(1)).isEqualTo("3");
        assertThat(order.getItems().get(0).getVariant()).isSameAs(xl);
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualTo(150.0);
    }

    @Test
    void cancellingAVariantOrderReturnsTheVariantStock() {
        Order order = existing(OrderStatus.PENDING, 1, 2);
        ProductVariant xl = StockServiceTest.variant(10, db.get(1L), "XL", null, 0);
        order.getItems().get(0).setVariant(xl);

        service.cancelOrder(12L);

        assertThat(xl.getQuantity()).isEqualTo(2);
        assertThat(stockOf(1)).isEqualTo("7");
    }
}
