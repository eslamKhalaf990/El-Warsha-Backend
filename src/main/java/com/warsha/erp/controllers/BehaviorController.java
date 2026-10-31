package com.warsha.erp.controllers;

import com.warsha.erp.dtos.BehaviorDtos.*;
import com.warsha.erp.services.BehaviorReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Store visitor behavior (admin only). Every endpoint takes {@code from} / {@code to} as
 * yyyy-MM-dd, both inclusive and optional: no {@code to} is today, no {@code from} is the
 * 30 days ending on {@code to}. At most one year.
 */
@RestController
@RequestMapping("/analytics/behavior")
public class BehaviorController {

    private final BehaviorReportService service;

    public BehaviorController(BehaviorReportService service) {
        this.service = service;
    }

    /** Visitors, sessions, the funnel from product view to order, devices and a daily trend. */
    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(from, to);
    }

    /** Views, cart adds/removes and tracked orders per product. */
    @GetMapping("/products")
    public List<ProductRow> products(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.products(from, to);
    }

    /** Carts left with items; {@code idleMinutes} (default 60) is how long without a change counts as left. */
    @GetMapping("/abandoned-carts")
    public AbandonedCarts abandonedCarts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Integer idleMinutes) {
        return service.abandonedCarts(from, to, idleMinutes);
    }

    /** What visitors searched for, and searches that found nothing. */
    @GetMapping("/searches")
    public Searches searches(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.searches(from, to);
    }

    /** Promo codes typed at the cart, applied or rejected and why. */
    @GetMapping("/vouchers")
    public List<VoucherRow> vouchers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.vouchers(from, to);
    }

    /** Where visitors came from and how many ordered. */
    @GetMapping("/sources")
    public List<SourceRow> sources(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.sources(from, to);
    }
}
