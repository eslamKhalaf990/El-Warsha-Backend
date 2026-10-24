package com.warsha.erp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.*;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.BusinessRuleException;
import com.warsha.erp.exceptions.NotFoundException;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final OrderLedgerService ledger;
    private final StockService stockService;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Autowired
    public OrderService(OrderRepository orderRepo,
                        CustomerService customerService,
                        InvoiceService invoiceService, EmailService emailService,
                        PaymentService paymentService, OrderLedgerService ledger,
                        StockService stockService) {
        this.orderRepository = orderRepo;
        this.customerService = customerService;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.paymentService = paymentService;
        this.ledger = ledger;
        this.stockService = stockService;
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(logFormat);
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        System.out.println("[" + getTimestamp() + "] INFO: Starting ERP Order creation for Customer ID: " + request.getCustomerId());
        Customer customer = customerService.getCustomerByID(request.getCustomerId());

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

        Order savedOrder = orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: ERP Order #" + savedOrder.getId() + " saved.");

        invoiceService.generateInvoice(savedOrder.getId());
        ledger.recordDownPayment(savedOrder, pricing.downPayment(), request.getPaymentMethod(), request.getBankAccountId());

        return savedOrder;
    }

    //E-Commerce order with image attachments and email notification
    @Transactional
    public Order placeOrder(CreateOrderRequest request, Long customerId, List<MultipartFile> images) {
        System.out.println("[" + getTimestamp() + "] INFO: Starting E-Commerce Order for Customer ID: " + customerId);
        Customer customer = customerService.getCustomerByID(customerId);

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.PENDING);
        order.setOrderSource(request.getOrderSource());
        order.setNotes(request.getNotes());
        order.setItems(new ArrayList<>());

        // Store customers can't set prices or extra discounts; delivery comes from their governorate.
        Map<Long, Product> products = stockService.lock(productIds(request));
        OrderPricing pricing = fillItems(order, request, products, true,
                0, EgyptGovernorates.getDeliveryPrice(customer.getGovernorate()), request.getDownPayment());

        Order savedOrder = orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: E-Commerce Order #" + savedOrder.getId() + " saved.");

        invoiceService.generateInvoice(savedOrder.getId());
        ledger.recordDownPayment(savedOrder, pricing.downPayment(), request.getPaymentMethod(), request.getBankAccountId());

        List<String> attachments = new ArrayList<>();
        try {
            attachments = emailService.saveImagesToTemp(images);
        } catch (IOException e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Image temp storage failed: " + e.getMessage());
        }

        emailService.sendNewOrderNotification(customer.getFullName(), savedOrder.getId().toString(), savedOrder.getTotalPrice(), attachments);

        return savedOrder;
    }

    @Transactional
    public Order updateOrder(Long orderId, CreateOrderRequest request) {
        System.out.println("[" + getTimestamp() + "] WARN: Processing update for Order #" + orderId);
        Order existingOrder = findOrder(orderId);
        if (!existingOrder.getStatus().isEditable()) {
            throw new BusinessRuleException("Order #" + orderId + " is " + existingOrder.getStatus().lower()
                    + " and can no longer be edited.");
        }

        existingOrder.setOrderSource(request.getOrderSource());
        existingOrder.setCustomer(customerService.getCustomerByID(request.getCustomerId()));
        existingOrder.setNotes(request.getNotes());

        // Lock old and new products together, return the old lines, then take the new ones.
        // Any failure rolls back the whole edit, so stock is never left half-updated.
        Set<Long> touched = new HashSet<>(productIds(request));
        existingOrder.getItems().forEach(i -> touched.add(i.getProduct().getProductID()));
        Map<Long, Product> products = stockService.lock(touched);

        stockService.putBackAll(existingOrder.getItems());
        existingOrder.getItems().clear();
        OrderPricing pricing = fillItems(existingOrder, request, products, false,
                request.getDiscount(), request.getDelivery(), request.getDownPayment());

        Order savedOrder = orderRepository.save(existingOrder);
        invoiceService.regenerateInvoice(savedOrder.getId());
        ledger.replaceDownPayment(savedOrder, pricing.downPayment(), request.getPaymentMethod(), request.getBankAccountId());

        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " updated successfully.");
        return savedOrder;
    }

    /**
     * Takes stock for every requested line, adds the lines to the order and applies
     * {@link OrderPricing}. Store orders always use the catalogue price; ERP orders may
     * override it per line. Product discounts apply to both.
     */
    private OrderPricing fillItems(Order order, CreateOrderRequest request, Map<Long, Product> products,
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

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus target, Long bankAccountId) {
        if (target == null) {
            throw new ValidationException("status", "Choose the new status for the order.");
        }
        if (target == OrderStatus.CANCELLED) {
            return cancelOrder(orderId);
        }

        Order existingOrder = findOrder(orderId);
        ensureTransition(existingOrder, target);
        if (target == OrderStatus.COMPLETED && bankAccountId == null) {
            throw new ValidationException("bankAccountId", "Choose the bank account that received the final payment.");
        }

        System.out.println("[" + getTimestamp() + "] INFO: Order #" + orderId + ": " + existingOrder.getStatus() + " -> " + target);
        existingOrder.setStatus(target);

        if (target == OrderStatus.COMPLETED) {
            ledger.recordFinalPayment(existingOrder, bankAccountId);
        } else if (target == OrderStatus.PROCESSING) {
            emailService.sendOrderProcessingEmail(
                    existingOrder.getCustomer().getEmail(),
                    existingOrder.getCustomer().getFullName(),
                    existingOrder.getId().toString(),
                    existingOrder.getTotalPrice()
            );
        }

        return orderRepository.save(existingOrder);
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

    private static List<Long> productIds(CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ValidationException("items", "Add at least one product to the order.");
        }
        for (OrderItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new ValidationException("items", "Every order line needs a product.");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new ValidationException("items", "Every order line needs a quantity of at least 1.");
            }
        }
        return request.getItems().stream().map(OrderItemRequest::getProductId).toList();
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    public List<OrderResponse> getAllOrders(LocalDate userStart, LocalDate userEnd) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching orders for range: " + userStart + " to " + userEnd);
        LocalDate start = (userStart != null) ? userStart : YearMonth.now().atDay(1);
        LocalDate end = (userEnd != null) ? userEnd : YearMonth.now().atEndOfMonth();

        List<Order> orders = orderRepository.findByOrderDateBetween(start, end, Sort.by(Sort.Direction.DESC, "id"));
        return orders.stream().map(this::convertToOrderResponse).collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        System.out.println("[" + getTimestamp() + "] INFO: Fetching orders for Customer ID: " + customerId);
        List<Order> orders = orderRepository.findByCustomerId(customerId, Sort.by(Sort.Direction.DESC, "id"));
        return orders.stream().map(this::convertToOrderResponse).collect(Collectors.toList());
    }

    private OrderResponse convertToOrderResponse(Order order) {
        List<PaymentDto> payments = paymentService.getPaymentsByOrder(order.getId());
        OrderResponse dto = new OrderResponse();
        dto.setOrderId(order.getId());
        dto.setNotes(order.getNotes());
        if (!payments.isEmpty()) {
            dto.setPaymentMethod(payments.getFirst().getPaymentMethod());
        }
        dto.setOrderDate(order.getOrderDate());
        dto.setStatus(order.getStatus());
        dto.setAllowedNextStatuses(order.getStatus().allowedNext());
        dto.setOrderSource(order.getOrderSource());
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
        )).collect(Collectors.toList()));

        return dto;
    }

    @Transactional
    public void deleteOrder(Long orderId) {
        System.out.println("[" + getTimestamp() + "] ALERT: Deleting Order #" + orderId);
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
        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " deleted.");
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        System.out.println("[" + getTimestamp() + "] WARN: Cancelling Order #" + orderId);
        Order order = findOrder(orderId);
        ensureTransition(order, OrderStatus.CANCELLED);

        stockService.lock(order.getItems().stream().map(i -> i.getProduct().getProductID()).toList());
        stockService.putBackAll(order.getItems());

        ledger.reverseAll(order, "order cancelled");
        paymentService.cancelPaymentsByOrderId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
            emailService.sendOrderCancellationEmail(
                    order.getCustomer().getEmail(),
                    order.getCustomer().getFullName(),
                    order.getId().toString()
            );
        Order saved = orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " cancelled.");
        return saved;
    }

    public List<OrderCountByGovernorateDto> getOrderCountsByGovernorate() {
        return orderRepository.countOrdersByGovernorate();
    }
}