package com.warsha.erp.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One store browser, identified by the random id the store app keeps. */
@Setter
@Getter
@Entity
@Table(name = "StoreVisitors")
public class StoreVisitor {

    @Id
    @Column(name = "VisitorID", length = 36)
    private String id;

    /** Set once the visitor logs in; null for guests. */
    @Column(name = "CustomerID")
    private Long customerId;

    @Column(name = "FirstSeenAt", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "LastSeenAt", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "FirstLandingPage")
    private String firstLandingPage;

    @Column(name = "FirstReferrer")
    private String firstReferrer;

    @Column(name = "FirstUtmSource")
    private String firstUtmSource;

    @Column(name = "FirstUtmMedium")
    private String firstUtmMedium;

    @Column(name = "FirstUtmCampaign")
    private String firstUtmCampaign;

    @Column(name = "DeviceType")
    private String deviceType;
}
