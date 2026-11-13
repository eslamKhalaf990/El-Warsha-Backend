package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The latest cart of one store visitor, as last reported by the store app. */
@Setter
@Getter
@Entity
@Table(name = "StoreCarts")
public class StoreCart {

    @Id
    @Column(name = "VisitorID", length = 36)
    private String visitorId;

    @Column(name = "CustomerID")
    private Long customerId;

    /** JSON list of {productId, variantId, quantity}. */
    @Column(name = "Items", nullable = false)
    private String items;

    @Column(name = "ItemCount", nullable = false)
    private int itemCount;

    /** Priced by the server from current product prices, never trusted from the browser. */
    @Column(name = "CartValue", nullable = false)
    private BigDecimal cartValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false)
    private StoreCartStatus status;

    @Column(name = "OrderID")
    private Long orderId;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "OrderedAt")
    private LocalDateTime orderedAt;
}
