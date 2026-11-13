package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One tracked store action. Append-only; ids are plain numbers, not foreign keys. */
@Setter
@Getter
@Entity
@Table(name = "StoreEvents")
public class StoreEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EventID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "EventType", nullable = false)
    private StoreEventType type;

    @Column(name = "VisitorID", nullable = false)
    private String visitorId;

    @Column(name = "SessionID", nullable = false)
    private String sessionId;

    @Column(name = "CustomerID")
    private Long customerId;

    @Column(name = "ProductID")
    private Long productId;

    @Column(name = "VariantID")
    private Long variantId;

    @Column(name = "Quantity")
    private Integer quantity;

    @Column(name = "OrderID")
    private Long orderId;

    @Column(name = "OrderValue")
    private BigDecimal orderValue;

    @Column(name = "SearchQuery")
    private String searchQuery;

    @Column(name = "ResultCount")
    private Integer resultCount;

    @Column(name = "VoucherCode")
    private String voucherCode;

    @Column(name = "VoucherResult")
    private String voucherResult;

    @Column(name = "PagePath")
    private String pagePath;

    @Column(name = "Referrer")
    private String referrer;

    @Column(name = "UtmSource")
    private String utmSource;

    @Column(name = "UtmMedium")
    private String utmMedium;

    @Column(name = "UtmCampaign")
    private String utmCampaign;

    @Column(name = "DeviceType")
    private String deviceType;

    @Column(name = "OccurredAt", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ReceivedAt", nullable = false)
    private LocalDateTime receivedAt;
}
