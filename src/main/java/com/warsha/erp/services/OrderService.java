package com.warsha.erp.services;

import com.warsha.erp.dtos.*;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class OrderService {

    /** Longest date range the order list accepts, to keep the response a sensible size. */
    static final long MAX_RANGE_DAYS = 366;

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final OrderLedgerService ledger;
    private final StockService stockService;

    public OrderService(OrderRepository orderRepository,
                        CustomerService customerService,
                        InvoiceService invoiceService, EmailService emailService,
                        PaymentService paymentService, OrderLedgerService ledger,
                        StockService stockService) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.paymentService = paymentService;
        this.ledger = ledger;
        this.stockService = stockService;
    }

    // ------------------------------------------------------------------ reads

    /** ERP list. Defaults to the current month; at most {@link #MAX_RANGE_DAYS} days. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(LocalDate from, LocalDate to, OrderStatus status) {
        LocalDate start = from != null ? from : YearMonth.now().atDay(1);
        LocalDate end = to != null ? to : (from != null ? start.plusMonths(1).minusDays(1) : YearMonth.now().atEndOfMonth());
        if (end.isBefore(start)) {
            throw new ValidationException("to", "The end date can't be before the start date.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new ValidationException("to", "Choose a date range of at most one year.");
        }
        return toResponses(orderRepository.findForList(start, end, status));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return toResponse(findOrder(orderId));
    }

    /** Store: the logged-in customer's own orders. */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return toResponses(orderRepository.findForCustomer(customerId));
    }

    public List<OrderCountByGovernorateDto> getOrderCountsByGovernorate() {
        return orderRepository.countOrdersByGovernorate();
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public Order createOrder(OrderRequest request) {
        Customer customer = requireCustomer(request.getCustomerId());

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.PENDING);
        order.setOrderSource(request.getOrderSource());
        order.setNotes(request.getNotes());
        order.setItems(new ArrayList<>());

        Map<Long, Product> products = stockService.lock(productIds(request));
        OrderPricing pricing = fillItems(order, request, products, false,
                request.getDiscount(), request.getDelivery(), request.getDownPayment());

        Order saved = orderRepository.save(order);
        invoiceService.generateInvoice(saved.getId());
        ledger.recordDownPayment(saved, pricing.downPayment(), request.getPaymentMethod(), request.getBankAccountId());

        log.info("ERP order #{} created for customer #{} (due {} EGP)", saved.getId(), customer.getId(), pricing.amountDue());
        return saved;
    }

    /** Store checkout. Prices, delivery and the receiving bank account are decided by the server. */
    @Transactional
    public Order placeOrder(OrderRequest request, Long customerId, List<MultipartFile> images) {
        Customer customer = customerService.getCustomerByID(customerId);

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.PENDING);
        order.setOrderSource(request.getOrderSource());
        order.setNotes(request.getNotes());
        order.setItems(new ArrayList<>());

        Map<Long, Product> products = stockService.lock(productIds(request));
        OrderPricing pricing = fillItems(order, request, products, true,
                0, EgyptGovernorates.getDeliveryPrice(customer.getGovernorate()), request.getDownPayment());

        Order saved = orderRepository.save(order);
        invoiceService.generateInvoice(saved.getId());
        ledger.recordDownPayment(saved, pricing.downPayment(), request.getPaymentMethod(), null);
        log.info("Store order #{} placed by customer #{} (due {} EGP)", saved.getId(), customerId, pricing.amountDue());

        List<String> attachments = new ArrayList<>();
        try {
            attachments = emailService.saveImagesToTemp(images);
        } catch (IOException e) {
            log.warn("Could not store receipt images for order #{}: {}", saved.getId(), e.getMessage());
        }
        List<String> files = attachments;
        afterCommit(() -> emailService.sendNewOrderNotification(
                customer.getFullName(), saved.getId().toString(), saved.getTotalPrice(), files));

        return saved;
    }

    @Transactional
    public Order updateOrder(Long orderId, OrderRequest request) {
        Order order = findOrder(orderId);
        if (!order.getStatus().isEditable()) {
            throw new BusinessRuleException("Order #" + orderId + " is " + order.getStatus().lower()
                    + " and can no longer be edited.");
        }

        order.setOrderSource(request.getOrderSource());
        order.setCustomer(requireCustomer(request.getCustomerId()));
        order.setNotes(request.getNotes());

        // Lock old and new products together, return the old lines, then take the new ones.
        // Any failure rolls back the whole edit, so stock is never left half-updated.
        Set<Long> touched = new HashSet<>(productIds(request));
        order.getItems().forEach(i -> touched.add(i.getProduct().getProductID()));
        Map<Long, Product> products = stockService.lock(touched);

        stockService.putBackAll(order.getItems());
        order.getItems().clear();
        OrderPricing pricing = fillItems(order, request, products, false,
                request.getDiscount(), request.getDelivery(), request.getDownPayment());

        Order saved = orderRepository.save(order);
        invoiceService.regenerateInvoice(saved.getId());
        ledger.replaceDownPayment(saved, pricing.downPayment(), request.getPaymentMethod(), request.getBankAccountId());

        log.info("Order #{} edited (due {} EGP)", orderId, pricing.amountDue());
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus target, Long bankAccountId) {
        if (target == null) {
            throw new ValidationException("status", "Choose the new status for the order.");
        }
        if (target == OrderStatus.CANCELLED) {
            return cancelOrder(orderId);
        }

        Order order = findOrder(orderId);
        ensureTransition(order, target);
        if (target == OrderStatus.COMPLETED && bankAccountId == null) {
            throw new ValidationException("bankAccountId", "Choose the bank account that received the final payment.");
        }

        log.info("Order #{}: {} -> {}", orderId, order.getStatus(), target);
        order.setStatus(target);

        if (target == OrderStatus.COMPLETED) {
            ledger.recordFinalPayment(order, bankAccountId);
        } else if (target == OrderStatus.PROCESSING) {
            Customer c = order.getCustomer();
            double due = order.getTotalPrice();
            afterCommit(() -> emailService.sendOrderProcessingEmail(c.getEmail(), c.getFullName(), orderId.toString(), due));
        }

        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = findOrder(orderId);
        ensureTransition(order, OrderStatus.CANCELLED);

        stockService.lock(order.getItems().stream().map(i -> i.getProduct().getProductID()).toList());
        stockService.putBackAll(order.getItems());

        ledger.reverseAll(order, "order cancelled");
        paymentService.cancelPaymentsByOrderId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        Customer c = order.getCustomer();
        afterCommit(() -> emailService.sendOrderCancellationEmail(c.getEmail(), c.getFullName(), orderId.toString()));

        log.info("Order #{} cancelled", orderId);
        return saved;
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = findOrder(orderId);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.COMPLETED) {
            throw new BusinessRuleException("Order #" + orderId + " is completed and can't be deleted.");
        }
        if (status != OrderStatus.PENDING && status != OrderStatus.CANCELLED) {
            throw new BusinessRuleException("Order #" + orderId + " is " + status.lower()
                    + ". Cancel it instead of deleting it.");
        }

        // A cancelled order already returned its stock; only a pending one still holds it.
        if (status == OrderStatus.PENDING) {
            stockService.lock(order.getItems().stream().map(i -> i.getProduct().getProductID()).toList());
            stockService.putBackAll(order.getItems());
        }

        ledger.reverseAll(order, "order deleted");
        paymentService.deletePaymentsByOrderId(orderId);
        invoiceService.deleteInvoiceByOrderId(orderId);
        orderRepository.delete(order);
        log.info("Order #{} deleted", orderId);
    }

    // ---------------------------------------------------------------- mapping

    /** The single place that turns an order into its API shape. */
    @Transactional(readOnly = true)
    public OrderResponse toResponse(Order order) {
        return toResponse(order, paymentService.getPaymentMethodsByOrder(List.of(order.getId())));
    }

    private List<OrderResponse> toResponses(List<Order> orders) {
        Map<Long, String> methods = paymentService.getPaymentMethodsByOrder(
                orders.stream().map(Order::getId).toList());
        return orders.stream().map(o -> toResponse(o, methods)).toList();
    }

    private static OrderResponse toResponse(Order order, Map<Long, String> paymentMethods) {
        OrderResponse dto = new OrderResponse();
        dto.setOrderId(order.getId());
        dto.setOrderDate(order.getOrderDate());
        dto.setStatus(order.getStatus());
        dto.setAllowedNextStatuses(order.getStatus().allowedNext());
        dto.setOrderSource(order.getOrderSource());
        dto.setNotes(order.getNotes());
        dto.setPaymentMethod(paymentMethods.get(order.getId()));
        dto.setDiscount(order.getDiscount());
        dto.setDelivery(order.getDeliveryCharge());
        dto.applyTotals(order);

        Customer c = order.getCustomer();
        if (c != null) {
            CustomerDto cDto = new CustomerDto();
            cDto.setCustomerId(c.getId());
            cDto.setFullName(c.getFullName());
            cDto.setGovernorate(c.getGovernorate());
            cDto.setPhone(c.getPhone());
            cDto.setSecondaryPhone(c.getSecondaryPhone());
            cDto.setCity(c.getCity());
            cDto.setAddress(c.getAddress());
            dto.setCustomer(cDto);
        }

        dto.setOrderItems(order.getItems().stream().map(i -> new OrderItemDto(
                i.getProduct().getProductID(), i.getProduct().getName(), i.getQuantity(), i.getUnitPrice()
        )).toList());
        return dto;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Takes stock for every requested line, adds the lines to the order and applies
     * {@link OrderPricing}. Store orders always use the catalogue price; ERP orders may
     * override it per line. Product discounts apply to both.
     */
    private OrderPricing fillItems(Order order, OrderRequest request, Map<Long, Product> products,
                                   boolean catalogPricesOnly, double orderDiscount, double delivery, double downPayment) {
        List<OrderPricing.Line> lines = new ArrayList<>();
        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = products.get(itemReq.getProductId());
            stockService.take(product, itemReq.getQuantity());

            double unitPrice = catalogPricesOnly || itemReq.getUnitPrice() == null
                    ? product.getSellingPrice() : itemReq.getUnitPrice();
            if (unitPrice < 0) {
                throw new ValidationException("items", "The price of '" + product.getName() + "' can't be negative.");
            }
            double unitDiscount = product.getDiscount() != null ? product.getDiscount() : 0;
            lines.add(new OrderPricing.Line(unitPrice, itemReq.getQuantity(), unitDiscount));

            OrderItems item = new OrderItems();
            item.setOrder(order);
            item.setProduct(product);
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemReq.getQuantity());
            order.getItems().add(item);
        }

        OrderPricing pricing = OrderPricing.calculate(lines, orderDiscount, delivery, downPayment);
        order.setDeliveryCharge(pricing.delivery().doubleValue());
        order.setDiscount(pricing.totalDiscount().doubleValue());
        order.setTotalPrice(pricing.amountDue().doubleValue());
        return pricing;
    }

    /** Throws a user-facing 422 explaining why the order can't move to {@code target}. */
    private void ensureTransition(Order order, OrderStatus target) {
        OrderStatus current = order.getStatus();
        if (current.canMoveTo(target)) return;

        String id = "Order #" + order.getId();
        if (current == target) {
            throw new BusinessRuleException(id + " is already " + current.lower() + ".");
        }
        if (current.isFinal()) {
            throw new BusinessRuleException(id + " is " + current.lower() + " and its status can no longer be changed.");
        }
        throw new BusinessRuleException(id + " can't go back from " + current + " to " + target + ".");
    }

    /** Defensive checks for callers that skip bean validation (services, tests). */
    private static List<Long> productIds(OrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ValidationException("items", "Add at least one product to the order.");
        }
        for (OrderItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new ValidationException("items", "Choose a product for every order line.");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ValidationException("items", "Quantity must be at least 1.");
            }
        }
        return request.getItems().stream().map(OrderItemRequest::getProductId).toList();
    }

    private Customer requireCustomer(Long customerId) {
        if (customerId == null) {
            throw new ValidationException("customerId", "Choose a customer for the order.");
        }
        return customerService.getCustomerByID(customerId);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /**
     * Runs {@code action} only after the current transaction commits, so a slow or failing
     * email can never roll back or delay the saved order. Errors are logged, not thrown.
     */
    private static void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Post-commit task failed: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
