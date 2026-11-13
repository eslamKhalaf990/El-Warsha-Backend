package com.warsha.erp.controllers;

import com.warsha.erp.config.CustomerUserDetails;
import com.warsha.erp.dtos.OrderCountByGovernorateDto;
import com.warsha.erp.dtos.OrderRequest;
import com.warsha.erp.dtos.OrderResponse;
import com.warsha.erp.dtos.OrderStatusRequest;
import com.warsha.erp.entities.Order;
import com.warsha.erp.entities.OrderStatus;
import com.warsha.erp.services.OrderService;
import com.warsha.erp.services.StoreTrackingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Orders API.
 *
 * <pre>
 * ERP (ADMIN)
 *   GET    /orders?from=&to=&status=    list (defaults to this month, max one year)
 *   GET    /orders/{id}
 *   POST   /orders                      create            → 201
 *   PUT    /orders/{id}                 edit (Pending / Processing only)
 *   PATCH  /orders/{id}/status          { status, bankAccountId? }
 *   POST   /orders/{id}/cancel          refund down payment, restock
 *   DELETE /orders/{id}                 Pending / Cancelled only → 204
 *   GET    /orders/stats/by-governorate
 *
 * Store (CUSTOMER)
 *   POST   /orders/checkout             multipart: order (JSON) + images;
 *                                        optional X-Visitor-Id / X-Session-Id link it to tracking
 *   GET    /orders/mine
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final StoreTrackingService trackingService;

    public OrderController(OrderService orderService, StoreTrackingService trackingService) {
        this.orderService = orderService;
        this.trackingService = trackingService;
    }

    // ------------------------------------------------------------------- ERP

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.listOrders(from, to, status);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(orderService.toResponse(order));
    }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return orderService.toResponse(orderService.updateOrder(id, request));
    }

    @PatchMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return orderService.toResponse(
                orderService.updateOrderStatus(id, request.getStatus(), request.getBankAccountId()));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return orderService.toResponse(orderService.cancelOrder(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats/by-governorate")
    public List<OrderCountByGovernorateDto> countsByGovernorate() {
        return orderService.getOrderCountsByGovernorate();
    }

    // ----------------------------------------------------------------- Store

    /** "/placeOrder" is the pre-v2 path, kept so installed store apps keep working. */
    @PostMapping(value = {"/checkout", "/placeOrder"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestPart("order") OrderRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal CustomerUserDetails customer,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        Order order = orderService.placeOrder(request, customer.getId(), images);
        OrderResponse response = orderService.toResponse(order);
        try {
            trackingService.recordOrder(visitorId, sessionId, customer.getId(), order.getId(), response.getGrandTotal());
        } catch (RuntimeException e) {
            // The order is already saved; losing its tracking row must not fail the checkout.
            log.warn("Could not record tracking for order #{}: {}", order.getId(), e.getMessage());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/orders/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    /** "/customer" is the pre-v2 path, kept so installed store apps keep working. */
    @GetMapping({"/mine", "/customer"})
    public List<OrderResponse> mine(@AuthenticationPrincipal CustomerUserDetails customer) {
        return orderService.getOrdersByCustomer(customer.getId());
    }
}
