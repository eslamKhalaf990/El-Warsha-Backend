package com.warsha.erp.dtos;

import java.time.LocalDate;
import java.util.List;

/**
 * Responses of {@code /analytics}. Money is in EGP, rounded to piasters; rates
 * and shares are fractions (0.25 = 25%).
 *
 * Terms used throughout: sales = Σ quantity × unit price; netSales = sales − discounts;
 * orderValue = netSales + delivery; grossProfit = netSales − cost (at today's buying
 * prices). Cancelled orders only count towards cancellation figures.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** The range asked for and the one just before it of the same length (null for all time). */
    public record Period(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, long days) {
    }

    public record Kpis(
            long orders,
            long cancelledOrders,
            double cancellationRate,
            long units,
            double sales,
            double discounts,
            double netSales,
            double deliveryFees,
            double orderValue,
            double cost,
            double grossProfit,
            double grossMargin,
            double averageOrderValue,
            double itemsPerOrder,
            long customers,
            long newCustomers,
            long returningCustomers,
            double collected,
            double outstanding
    ) {
    }

    public record Overview(Period period, Kpis current, Kpis previous) {
    }

    /** One bucket of the trend; {@code start} is its first day. */
    public record TrendPoint(LocalDate start, long orders, double netSales, double grossProfit, double discounts) {
    }

    /** {@code granularity} is day, week (starting Saturday) or month. */
    public record Trend(Period period, String granularity, List<TrendPoint> points) {
    }

    public record CategoryRow(String category, long units, double netSales, double grossProfit,
                              double grossMargin, double share) {
    }

    /** {@code daysOfCover}: how long the stock lasts at the range's sales rate (null when it didn't sell). */
    public record ProductRow(Long productId, String name, String category, long units, double netSales,
                             double grossProfit, double grossMargin, Integer stock, Double daysOfCover) {
    }

    /** In stock but not sold in the range; {@code stockValue} is at buying price. */
    public record SlowMover(Long productId, String name, String category, int stock, double stockValue,
                            LocalDate lastSold) {
    }

    public record Products(Period period, List<CategoryRow> categories, List<ProductRow> topProducts,
                           List<ProductRow> runningLow, List<SlowMover> slowMovers) {
    }

    public record ChannelRow(String channel, long orders, long cancelled, double cancellationRate,
                             double netSales, double averageOrderValue, double share) {
    }

    public record RegionRow(String governorate, long orders, long customers, double netSales,
                            double deliveryFees, double averageOrderValue, double share) {
    }

    public record CustomerRow(Long customerId, String name, String phone, String governorate, long orders,
                              double spend, LocalDate lastOrder) {
    }

    /** {@code repeatRate}: share of the range's customers who have ordered more than once. */
    public record Customers(Period period, long customers, long newCustomers, long returningCustomers,
                            double repeatRate, double averageSpend, List<CustomerRow> topCustomers) {
    }

    /** How the down payments were made. */
    public record MethodRow(String method, long orders, double amount, double share) {
    }

    /** {@code outstanding}: what is still to collect on orders in this status. */
    public record StatusRow(String status, long orders, double orderValue, double outstanding) {
    }

    /** {@code dayOfWeek}: 1 = Monday … 7 = Sunday. */
    public record WeekdayRow(String weekday, int dayOfWeek, long orders, double netSales) {
    }

    public record Cash(Period period, double collected, double outstanding, List<MethodRow> paymentMethods,
                       List<StatusRow> statuses, List<WeekdayRow> weekdays) {
    }
}
