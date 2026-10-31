package com.warsha.erp.services;

import com.warsha.erp.dtos.AnalyticsDtos.*;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.AnalyticsRepository;
import com.warsha.erp.services.AnalyticsCalculator.OrderFact;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The analytics dashboard. Every section takes an optional date range: no
 * {@code to} means today, no {@code from} means since the first order (all time).
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Set<String> GRANULARITIES = Set.of("day", "week", "month");

    private final AnalyticsRepository repository;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    AnalyticsService(AnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Overview overview(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        LocalDate start = p.previousFrom() != null ? p.previousFrom() : p.from();
        List<OrderFact> orders = orders(start, p.to());
        Kpis current = AnalyticsCalculator.kpis(between(orders, p.from(), p.to()), p.from());
        Kpis previous = p.previousFrom() == null ? null
                : AnalyticsCalculator.kpis(between(orders, p.previousFrom(), p.previousTo()), p.previousFrom());
        return new Overview(p, current, previous);
    }

    public Trend trend(LocalDate from, LocalDate to, String granularity) {
        Period p = period(from, to);
        String g = granularity == null || granularity.isBlank() ? AnalyticsCalculator.granularityFor(p)
                : granularity.trim().toLowerCase();
        if (!GRANULARITIES.contains(g)) {
            throw new ValidationException("granularity", "Granularity must be day, week or month.");
        }
        return AnalyticsCalculator.trend(p, orders(p.from(), p.to()), g);
    }

    public Products products(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        List<AnalyticsCalculator.ProductSale> sales = repository.productSales(p.from(), p.to().plusDays(1)).stream()
                .map(AnalyticsCalculator.ProductSale::fromRow).toList();
        List<AnalyticsCalculator.StockItem> stock = repository.productStock().stream()
                .map(AnalyticsCalculator.StockItem::fromRow).toList();
        return AnalyticsCalculator.products(p, sales, stock);
    }

    public List<ChannelRow> channels(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        return AnalyticsCalculator.channels(orders(p.from(), p.to()));
    }

    public List<RegionRow> regions(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        return AnalyticsCalculator.regions(orders(p.from(), p.to()));
    }

    public Customers customers(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        return AnalyticsCalculator.customers(p, orders(p.from(), p.to()));
    }

    public Cash cash(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        return AnalyticsCalculator.cash(p, orders(p.from(), p.to()));
    }

    Period period(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        if (from != null && from.isAfter(end)) {
            throw new ValidationException("from", "The start date must be on or before the end date.");
        }
        if (from != null) return AnalyticsCalculator.period(from, end, false);
        LocalDate first = AnalyticsCalculator.toDate(repository.firstOrderDate());
        LocalDate start = first == null || first.isAfter(end) ? end : first;
        return AnalyticsCalculator.period(start, end, true);
    }

    private List<OrderFact> orders(LocalDate from, LocalDate to) {
        return repository.orderFacts(from, to.plusDays(1)).stream().map(OrderFact::fromRow).toList();
    }

    private static List<OrderFact> between(List<OrderFact> orders, LocalDate from, LocalDate to) {
        return orders.stream()
                .filter(o -> o.date() != null && !o.date().isBefore(from) && !o.date().isAfter(to))
                .toList();
    }
}
