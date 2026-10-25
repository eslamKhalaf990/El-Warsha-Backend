package com.warsha.erp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.*;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.InsufficientStockException;
import com.warsha.erp.repository.OrderRepository;
import com.warsha.erp.repository.ProductRepository;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final BankTransactionService bankTransactionService;

    // Formatter for consistent log timestamps
    private final DateTimeFormatter logFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Autowired
    public OrderService(OrderRepository orderRepo, ProductRepository productRepository,
                        CustomerService customerService,
                        ProductService productService,
                        InvoiceService invoiceService, EmailService emailService,
                        PaymentService paymentService, BankTransactionService bankTransactionService) {
        this.orderRepository = orderRepo;
        this.productRepository = productRepository;
        this.customerService = customerService;
        this.productService = productService;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.paymentService = paymentService;
        this.bankTransactionService = bankTransactionService;
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
        order.setStatus("Pending");
        order.setOrderSource(request.getOrderSource());
        order.setDeliveryCharge(request.getDelivery());
        order.setNotes(request.getNotes());

        List<OrderItems> itemList = new ArrayList<>();
        BigDecimal calculatedDiscount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.getProductById(itemReq.getProductId());
            System.out.println("[" + getTimestamp() + "] INFO: Adding Product: " + product.getName() + " (Qty: " + itemReq.getQuantity() + ")");

            OrderItems item = new OrderItems();
            item.setOrder(order);
            item.setProduct(product);
            item.setUnitPrice(itemReq.getUnitPrice() == null ? product.getSellingPrice() : itemReq.getUnitPrice());
            item.setQuantity(itemReq.getQuantity());

            int oldValue = Integer.parseInt(product.getQuantity());
            product.setQuantity(String.valueOf(oldValue - itemReq.getQuantity()));
            int soldBefore = product.getSold() != null ? product.getSold() : 0;
            product.setSold(soldBefore + itemReq.getQuantity());

            calculatedDiscount = calculatedDiscount.add(
                    BigDecimal.valueOf(itemReq.getQuantity())
                            .multiply(BigDecimal.valueOf(product.getDiscount()))
            );

            productService.updateProduct(itemReq.getProductId(), product);
            itemList.add(item);
        }

        order.setDiscount(request.getDiscount() + calculatedDiscount.doubleValue());
        order.setTotalPrice(request.calculateTotalPriceERP() - order.getDiscount());

        order.setItems(itemList);
        Order savedOrder = orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: ERP Order #" + savedOrder.getId() + " saved.");

        invoiceService.generateInvoice(savedOrder.getId());

        processFinance(savedOrder.getId(), request.getDownPayment(), request.getPaymentMethod(), request.getBankAccountId(), "ERP Down Payment");

        return savedOrder;
    }

    //E-Commerce order with image attachments and email notification
    @Transactional
    public Order placeOrder(CreateOrderRequest request, Long customerId, List<MultipartFile> images) {
        System.out.println("[" + getTimestamp() + "] INFO: Starting E-Commerce Order for Customer ID: " + customerId);
        Customer customer = customerService.getCustomerByID(customerId);

        List<Long> productIds = request.getItems().stream().map(OrderItemRequest::getProductId).collect(Collectors.toList());
        List<Product> products = productRepository.findAllByIdWithLock(productIds);
        Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getProductID, Function.identity()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDate.now());
        order.setStatus("Pending");
        order.setOrderSource(request.getOrderSource());
        order.setDeliveryCharge(EgyptGovernorates.getDeliveryPrice(customer.getGovernorate()));
        order.setNotes(request.getNotes());

        List<OrderItems> orderItemsList = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        BigDecimal calculatedDiscount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            if (product == null) throw new IllegalArgumentException("Product ID " + itemReq.getProductId() + " not found.");

            int currentStock = Integer.parseInt(product.getQuantity());
            if (currentStock < itemReq.getQuantity()) {
                System.out.println("[" + getTimestamp() + "] ERROR: Out of stock for " + product.getName());
                throw new InsufficientStockException("Insufficient stock for: " + product.getName());
            }

            // todo:  - product.getDiscount()
            BigDecimal unitPrice = BigDecimal.valueOf(product.getSellingPrice());
            calculatedTotal = calculatedTotal.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            calculatedDiscount = calculatedDiscount.add(
                    BigDecimal.valueOf(itemReq.getQuantity())
                            .multiply(BigDecimal.valueOf(product.getDiscount()))
            );

            product.setQuantity(String.valueOf(currentStock - itemReq.getQuantity()));
            product.setSold((product.getSold() == null ? 0 : product.getSold()) + itemReq.getQuantity());
            product.setUpdatedAt(LocalDateTime.now());

            OrderItems item = new OrderItems();
            item.setOrder(order);
            item.setProduct(product);
            item.setUnitPrice(product.getSellingPrice());
            item.setQuantity(itemReq.getQuantity());
            orderItemsList.add(item);
        }

        productRepository.saveAll(products);

        order.setDiscount(calculatedDiscount.doubleValue());

        BigDecimal finalTotal = calculatedTotal.add(BigDecimal.valueOf(EgyptGovernorates.getDeliveryPrice(customer.getGovernorate())))
                .subtract(BigDecimal.valueOf(request.getDownPayment())).subtract(
                        calculatedDiscount
                );
        order.setTotalPrice(finalTotal.doubleValue());
        order.setItems(orderItemsList);

        Order savedOrder = orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: E-Commerce Order #" + savedOrder.getId() + " saved.");

        processPostOrderIntegrations(request, savedOrder);

        List<String> attachments = new ArrayList<>();
        try {
            attachments = emailService.saveImagesToTemp(images);
        } catch (IOException e) {
            System.out.println("[" + getTimestamp() + "] ERROR: Image temp storage failed: " + e.getMessage());
        }

        emailService.sendNewOrderNotification(savedOrder, request, attachments);

        return savedOrder;
    }

    private void processPostOrderIntegrations(CreateOrderRequest request, Order savedOrder) {
        System.out.println("[" + getTimestamp() + "] INFO: Integration processing for Order #" + savedOrder.getId());
        invoiceService.generateInvoice(savedOrder.getId());

        BigDecimal depositAmount = BigDecimal.valueOf(request.getDownPayment());
        if (request.getBankAccountId() != null && request.getBankAccountId() == 3L) {
            depositAmount = depositAmount.subtract(BigDecimal.valueOf(5));
        }

        BankTransactionDTO bankTx = new BankTransactionDTO();
        bankTx.setBankAccountId(request.getBankAccountId());
        bankTx.setTransactionType("Deposit");
        bankTx.setReferenceType("Order");
        bankTx.setReferenceId(savedOrder.getId());
        bankTx.setCategoryId(1L);
        bankTx.setDescription("Down payment for order #" + savedOrder.getId());
        bankTx.setAmount(depositAmount);
        bankTransactionService.createTransaction(bankTx);

        CreatePaymentRequest payReq = new CreatePaymentRequest();
        payReq.setOrderId(savedOrder.getId());
        payReq.setAmountPaid(request.getDownPayment());
        payReq.setPaymentMethod(request.getPaymentMethod());
        payReq.setPaymentStatus("Completed");
        paymentService.createPayment(payReq);
    }

    private void processFinance(Long orderId, Double downPayment, String method, Long bankId, String desc) {
        System.out.println("[" + getTimestamp() + "] INFO: Finance Entry - Order #" + orderId + ", Amount: " + downPayment + ", bankId: " + bankId);
        BankTransactionDTO bankTx = new BankTransactionDTO();
        bankId = (bankId != null) ? bankId : 1L;
        bankTx.setBankAccountId( bankId);
        bankTx.setTransactionType("Deposit");
        bankTx.setReferenceType("Order");
        bankTx.setReferenceId(orderId);
        bankTx.setCategoryId(1L);
        bankTx.setDescription(desc + " #" + orderId);
        bankTx.setAmount(BigDecimal.valueOf(bankId == 3L ? downPayment - 5 : downPayment));
        bankTransactionService.createTransaction(bankTx);

        CreatePaymentRequest payReq = new CreatePaymentRequest();
        payReq.setOrderId(orderId);
        payReq.setAmountPaid(downPayment);
        payReq.setPaymentMethod(method);
        payReq.setPaymentStatus("Completed");
        paymentService.createPayment(payReq);
    }

    @Transactional
    public Order updateOrder(Long orderId, CreateOrderRequest request) {
        System.out.println("[" + getTimestamp() + "] WARN: Processing update for Order #" + orderId);
        Order existingOrder = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        // Update fields
        existingOrder.setOrderSource(request.getOrderSource());
        existingOrder.setCustomer(customerService.getCustomerByID(request.getCustomerId()));
        existingOrder.setDeliveryCharge(request.getDelivery());
        existingOrder.setDiscount(request.getDiscount());
        existingOrder.setNotes(request.getNotes());
        existingOrder.setTotalPrice(request.calculateTotalPriceERP() - request.getDiscount());

        // Revert Stock
        for (OrderItems item : existingOrder.getItems()) {
            Product product = item.getProduct();
            int restoredQty = Integer.parseInt(product.getQuantity()) + item.getQuantity();
            product.setQuantity(String.valueOf(restoredQty));
            productService.updateProduct(product.getProductID(), product);
        }

        existingOrder.getItems().clear();
        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productService.getProductById(itemReq.getProductId());
            int oldValue = Integer.parseInt(product.getQuantity());
            if (oldValue < itemReq.getQuantity()) throw new RuntimeException("Insufficient stock for: " + product.getName());

            product.setQuantity(String.valueOf(oldValue - itemReq.getQuantity()));
            productService.updateProduct(product.getProductID(), product);

            OrderItems item = new OrderItems();
            item.setOrder(existingOrder);
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getSellingPrice());
            existingOrder.getItems().add(item);
        }

        Order savedOrder = orderRepository.save(existingOrder);
        invoiceService.regenerateInvoice(savedOrder.getId());
        paymentService.deletePaymentsByOrderId(savedOrder.getId());

        processFinance(savedOrder.getId(), request.getDownPayment(), request.getPaymentMethod(), request.getBankAccountId(), "Updated Order Down Payment");

        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " updated successfully.");
        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, String status, Long bankAccountId) {
        System.out.println("[" + getTimestamp() + "] INFO: Updating status for Order #" + orderId + " to: " + status);
        Order existingOrder = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        existingOrder.setStatus(status);

        if ("Completed".equals(status)) {
            System.out.println("[" + getTimestamp() + "] INFO: Order Completed. Recording final bank transaction.");
            BankTransactionDTO bankTx = new BankTransactionDTO();
            bankTx.setBankAccountId(bankAccountId);
            bankTx.setTransactionType("Deposit");
            bankTx.setReferenceType("Order");
            bankTx.setReferenceId(orderId);
            bankTx.setCategoryId(1L);
            bankTx.setDescription("Order #" + orderId + " Final Payment");
            bankTx.setAmount(BigDecimal.valueOf(existingOrder.getTotalPrice()));
            bankTransactionService.createTransaction(bankTx);
        } else if ("Processing".equals(status)){
            System.out.println("[" + getTimestamp() + "] INFO: Order Processing. No financial action required at this stage.");
            emailService.sendOrderProcessingEmail(
                    existingOrder.getCustomer().getEmail(), // Assuming phone is used as email for demo
                    existingOrder.getCustomer().getFullName(),
                    existingOrder.getId().toString(),
                    existingOrder.getTotalPrice()
            );
        }

        return orderRepository.save(existingOrder);
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
            dto.setDownPayment(payments.getFirst().getAmountPaid());
        }
        dto.setOrderDate(order.getOrderDate());
        dto.setStatus(order.getStatus());
        dto.setOrderSource(order.getOrderSource());
        dto.setDiscount(order.getDiscount());
        dto.setDelivery(order.getDeliveryCharge());
        dto.setTotalPrice(BigDecimal.valueOf(order.getTotalPrice()));

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
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        for (OrderItems item : order.getItems()) {
            Product p = item.getProduct();
            p.setQuantity(String.valueOf(Integer.parseInt(p.getQuantity()) + item.getQuantity()));
            productService.updateProduct(p.getProductID(), p);
        }

        paymentService.deletePaymentsByOrderId(orderId);
        invoiceService.deleteInvoiceByOrderId(orderId);
        orderRepository.delete(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " deleted and stock restored.");
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        System.out.println("[" + getTimestamp() + "] WARN: Cancelling Order #" + orderId);
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

        if ("Cancelled".equals(order.getStatus())) return;

        for (OrderItems item : order.getItems()) {
            Product p = item.getProduct();
            p.setQuantity(String.valueOf(Integer.parseInt(p.getQuantity()) + item.getQuantity()));
            productService.updateProduct(p.getProductID(), p);
        }

        paymentService.cancelPaymentsByOrderId(orderId);
        order.setStatus("Cancelled");
            emailService.sendOrderCancellationEmail(
                    order.getCustomer().getEmail(),
                    order.getCustomer().getFullName(),
                    order.getId().toString()
            );
        orderRepository.save(order);
        System.out.println("[" + getTimestamp() + "] SUCCESS: Order #" + orderId + " cancelled.");
    }

    public List<OrderCountByGovernorateDto> getOrderCountsByGovernorate() {
        return orderRepository.countOrdersByGovernorate();
    }
}