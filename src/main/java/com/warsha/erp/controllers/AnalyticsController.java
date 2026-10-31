package com.warsha.erp.controllers;

import com.warsha.erp.dtos.AnalyticsDtos.*;
import com.warsha.erp.services.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The ERP analytics dashboard (admin only). Every endpoint takes {@code from} / {@code to}
 * as yyyy-MM-dd, both inclusive and optional: no {@code to} is today, no {@code from}
 * is all time. Replaces the figures under {@code /cashFlow}, which stay for older clients.
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    /** Headline KPIs for the range and the period just before it. */
    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(from, to);
    }

    /** Sales over time; {@code granularity} is day, week or month (picked from the range when left out). */
    @GetMapping("/trend")
    public Trend trend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String granularity) {
        return service.trend(from, to, granularity);
    }

    /** Categories, best products, products running low and stock that isn't selling. */
    @GetMapping("/products")
    public Products products(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.products(from, to);
    }

    @GetMapping("/channels")
    public List<ChannelRow> channels(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.channels(from, to);
    }

    @GetMapping("/regions")
    public List<RegionRow> regions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.regions(from, to);
    }

    @GetMapping("/customers")
    public Customers customers(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.customers(from, to);
    }

    /** Money collected and still due, payment methods, the status pipeline and the busiest weekdays. */
    @GetMapping("/cash")
    public Cash cash(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.cash(from, to);
    }
}
