package com.warsha.erp.services;

import com.warsha.erp.dtos.AnalyticsDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out every dashboard figure from plain facts, without touching the database,
 * so the business rules live in one place and are easy to test. See
 * {@link com.warsha.erp.dtos.AnalyticsDtos} for what each term means.
 */
public final class AnalyticsCalculator {

    static final int TOP_PRODUCTS = 15;
    static final int TOP_CUSTOMERS = 10;
    static final int SLOW_MOVERS = 10;
    /** Products whose stock lasts less than this many days at the current rate are "running low". */
    static final double LOW_COVER_DAYS = 21;

    private AnalyticsCalculator() {
    }

    /** One order, as {@link com.warsha.erp.repository.AnalyticsRepository#orderFacts} returns it. */
    public record OrderFact(long orderId, LocalDate date, String status, String source, String governorate,
                            Long customerId, String customerName, String phone, LocalDate firstOrder,
                            long lifetimeOrders, double subtotal, double cost, long units, double discount,
                            double delivery, double due, double paid, String paymentMethod) {

        public boolean cancelled() {
            return "Cancelled".equalsIgnoreCase(status);
        }

        boolean completed() {
            return "Completed".equalsIgnoreCase(status);
        }

        double netSales() {
            return subtotal - discount;
        }

        double orderValue() {
            return netSales() + delivery;
        }

        double grossProfit() {
            return netSales() - cost;
        }

        /** The down payment, plus the rest once the order is completed. */
        double collected() {
            return cancelled() ? 0 : paid + (completed() ? due : 0);
        }

        double outstanding() {
            return cancelled() || completed() ? 0 : due;
        }

        public static OrderFact fromRow(Object[] r) {
            return new OrderFact(num(r[0]).longValue(), toDate(r[1]), str(r[2]), str(r[3]), str(r[4]),
                    r[5] == null ? null : num(r[5]).longValue(), str(r[6]), str(r[7]), toDate(r[8]),
                    num(r[9]).longValue(), num(r[10]).doubleValue(), num(r[11]).doubleValue(),
                    num(r[12]).longValue(), num(r[13]).doubleValue(), num(r[14]).doubleValue(),
                    num(r[15]).doubleValue(), num(r[16]).doubleValue(), str(r[17]));
        }
    }

    /** One product's sales in a range. {@code discount} is its share of the order discounts. */
    public record ProductSale(Long productId, String name, String category, long units, double sales,
                              double discount, double cost) {

        double netSales() {
            return sales - discount;
        }

        public static ProductSale fromRow(Object[] r) {
            return new ProductSale(num(r[0]).longValue(), str(r[1]), str(r[2]), num(r[3]).longValue(),
                    num(r[4]).doubleValue(), num(r[5]).doubleValue(), num(r[6]).doubleValue());
        }
    }

    /** A product that is not deleted, with what is left of it. */
    public record StockItem(Long productId, String name, String category, int stock, double unitCost,
                            LocalDate lastSold) {

        /** Products.Quantity is text; anything unreadable counts as no stock. */
        public static StockItem fromRow(Object[] r) {
            int stock;
            try {
                stock = r[3] == null ? 0 : (int) Double.parseDouble(r[3].toString().trim());
            } catch (NumberFormatException e) {
                stock = 0;
            }
            return new StockItem(num(r[0]).longValue(), str(r[1]), str(r[2]), stock, num(r[4]).doubleValue(),
                    toDate(r[5]));
        }
    }

    // --- Period ---

    /** {@code from} null means since the first order; there is then no previous period. */
    public static Period period(LocalDate from, LocalDate to, boolean allTime) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (allTime) return new Period(from, to, null, null, days);
        return new Period(from, to, from.minusDays(days), from.minusDays(1), days);
    }

    // --- Overview ---

    /** KPIs for the orders of one period; {@code from} decides who counts as a new customer. */
    public static Kpis kpis(List<OrderFact> all, LocalDate from) {
        List<OrderFact> orders = live(all);
        long cancelled = all.size() - orders.size();
        double sales = sum(orders, OrderFact::subtotal);
        double discounts = sum(orders, OrderFact::discount);
        double netSales = sales - discounts;
        double delivery = sum(orders, OrderFact::delivery);
        double cost = sum(orders, OrderFact::cost);
        long units = orders.stream().mapToLong(OrderFact::units).sum();
        Map<Long, OrderFact> customers = byCustomer(orders);
        long newCustomers = customers.values().stream().filter(o -> isNew(o, from)).count();
        return new Kpis(
                orders.size(),
                cancelled,
                ratio(cancelled, all.size()),
                units,
                money(sales),
                money(discounts),
                money(netSales),
                money(delivery),
                money(netSales + delivery),
                money(cost),
                money(netSales - cost),
                ratio(netSales - cost, netSales),
                money(orders.isEmpty() ? 0 : (netSales + delivery) / orders.size()),
                round(orders.isEmpty() ? 0 : (double) units / orders.size(), 2),
                customers.size(),
                newCustomers,
                customers.size() - newCustomers,
                money(sum(all, OrderFact::collected)),
                money(sum(all, OrderFact::outstanding)));
    }

    // --- Trend ---

    /** day up to 45 days, week up to 200, month beyond. */
    public static String granularityFor(Period period) {
        return period.days() <= 45 ? "day" : period.days() <= 200 ? "week" : "month";
    }

    /** One point per bucket of the period, empty buckets included, so gaps show as zero. */
    public static Trend trend(Period period, List<OrderFact> all, String granularity) {
        Map<LocalDate, List<OrderFact>> byBucket = live(all).stream()
                .collect(Collectors.groupingBy(o -> bucketStart(o.date(), granularity)));
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate start = bucketStart(period.from(), granularity); !start.isAfter(period.to());
             start = nextBucket(start, granularity)) {
            List<OrderFact> orders = byBucket.getOrDefault(start, List.of());
            points.add(new TrendPoint(start, orders.size(), money(sum(orders, OrderFact::netSales)),
                    money(sum(orders, OrderFact::grossProfit)), money(sum(orders, OrderFact::discount))));
        }
        return new Trend(period, granularity, points);
    }

    static LocalDate bucketStart(LocalDate d, String granularity) {
        return switch (granularity) {
            case "week" -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            case "month" -> d.withDayOfMonth(1);
            default -> d;
        };
    }

    private static LocalDate nextBucket(LocalDate d, String granularity) {
        return switch (granularity) {
            case "week" -> d.plusWeeks(1);
            case "month" -> d.plusMonths(1);
            default -> d.plusDays(1);
        };
    }

    // --- Products ---

    public static Products products(Period period, List<ProductSale> sales, List<StockItem> stock) {
        double totalNet = sales.stream().mapToDouble(ProductSale::netSales).sum();
        Map<Long, StockItem> stockById = stock.stream()
                .collect(Collectors.toMap(StockItem::productId, Function.identity(), (a, b) -> a));

        List<CategoryRow> categories = sales.stream()
                .collect(Collectors.groupingBy(s -> s.category() == null ? AnalyticsLabels.UNKNOWN : s.category().trim()))
                .entrySet().stream()
                .map(e -> {
                    double net = e.getValue().stream().mapToDouble(ProductSale::netSales).sum();
                    double cost = e.getValue().stream().mapToDouble(ProductSale::cost).sum();
                    long units = e.getValue().stream().mapToLong(ProductSale::units).sum();
                    return new CategoryRow(e.getKey(), units, money(net), money(net - cost),
                            ratio(net - cost, net), ratio(net, totalNet));
                })
                .sorted(Comparator.comparingDouble(CategoryRow::netSales).reversed())
                .toList();

        List<ProductRow> rows = sales.stream()
                .map(s -> {
                    StockItem item = stockById.get(s.productId());
                    Integer left = item == null ? null : item.stock();
                    Double cover = left == null || s.units() == 0 ? null
                            : round(Math.max(left, 0) / ((double) s.units() / period.days()), 1);
                    return new ProductRow(s.productId(), s.name(), s.category(), s.units(), money(s.netSales()),
                            money(s.netSales() - s.cost()), ratio(s.netSales() - s.cost(), s.netSales()), left, cover);
                })
                .toList();

        List<ProductRow> top = rows.stream()
                .sorted(Comparator.comparingDouble(ProductRow::netSales).reversed())
                .limit(TOP_PRODUCTS).toList();
        List<ProductRow> runningLow = rows.stream()
                .filter(r -> r.daysOfCover() != null && r.daysOfCover() < LOW_COVER_DAYS)
                .sorted(Comparator.comparingDouble(ProductRow::daysOfCover))
                .toList();

        Set<Long> sold = sales.stream().map(ProductSale::productId).collect(Collectors.toSet());
        List<SlowMover> slowMovers = stock.stream()
                .filter(s -> s.stock() > 0 && !sold.contains(s.productId()))
                .map(s -> new SlowMover(s.productId(), s.name(), s.category(), s.stock(),
                        money(s.stock() * s.unitCost()), s.lastSold()))
                .sorted(Comparator.comparingDouble(SlowMover::stockValue).reversed())
                .limit(SLOW_MOVERS).toList();

        return new Products(period, categories, top, runningLow, slowMovers);
    }

    // --- Channels and regions ---

    public static List<ChannelRow> channels(List<OrderFact> all) {
        double totalNet = sum(live(all), OrderFact::netSales);
        return all.stream()
                .collect(Collectors.groupingBy(o -> AnalyticsLabels.channel(o.source())))
                .entrySet().stream()
                .map(e -> {
                    List<OrderFact> orders = live(e.getValue());
                    long cancelled = e.getValue().size() - orders.size();
                    double net = sum(orders, OrderFact::netSales);
                    return new ChannelRow(e.getKey(), orders.size(), cancelled, ratio(cancelled, e.getValue().size()),
                            money(net), money(average(orders, OrderFact::orderValue)), ratio(net, totalNet));
                })
                .sorted(Comparator.comparingDouble(ChannelRow::netSales).reversed().thenComparing(ChannelRow::channel))
                .toList();
    }

    /** Spellings of one governorate are merged; the most used spelling names it. */
    public static List<RegionRow> regions(List<OrderFact> all) {
        List<OrderFact> orders = live(all);
        double totalNet = sum(orders, OrderFact::netSales);
        return orders.stream()
                .collect(Collectors.groupingBy(o -> AnalyticsLabels.governorateKey(o.governorate())))
                .values().stream()
                .map(group -> {
                    String name = group.stream()
                            .collect(Collectors.groupingBy(o -> AnalyticsLabels.governorate(o.governorate()), Collectors.counting()))
                            .entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                    double net = sum(group, OrderFact::netSales);
                    return new RegionRow(name, group.size(), byCustomer(group).size(), money(net),
                            money(sum(group, OrderFact::delivery)), money(average(group, OrderFact::orderValue)),
                            ratio(net, totalNet));
                })
                .sorted(Comparator.comparingDouble(RegionRow::netSales).reversed().thenComparing(RegionRow::governorate))
                .toList();
    }

    // --- Customers ---

    public static Customers customers(Period period, List<OrderFact> all) {
        List<OrderFact> orders = live(all);
        Map<Long, List<OrderFact>> byCustomer = orders.stream()
                .filter(o -> o.customerId() != null)
                .collect(Collectors.groupingBy(OrderFact::customerId));
        long newCustomers = byCustomer.values().stream().filter(os -> isNew(os.getFirst(), period.from())).count();
        long repeaters = byCustomer.values().stream().filter(os -> os.getFirst().lifetimeOrders() > 1).count();

        List<CustomerRow> top = byCustomer.values().stream()
                .map(os -> {
                    OrderFact any = os.getFirst();
                    LocalDate last = os.stream().map(OrderFact::date).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).orElse(null);
                    return new CustomerRow(any.customerId(), any.customerName(), any.phone(),
                            AnalyticsLabels.governorate(any.governorate()), os.size(),
                            money(sum(os, OrderFact::orderValue)), last);
                })
                .sorted(Comparator.comparingDouble(CustomerRow::spend).reversed())
                .limit(TOP_CUSTOMERS).toList();

        return new Customers(period, byCustomer.size(), newCustomers, byCustomer.size() - newCustomers,
                ratio(repeaters, byCustomer.size()),
                money(byCustomer.isEmpty() ? 0 : sum(orders, OrderFact::orderValue) / byCustomer.size()), top);
    }

    // --- Cash and operations ---

    private static final List<String> STATUS_ORDER = List.of("Pending", "Processing", "Shipped", "Completed", "Cancelled");

    public static Cash cash(Period period, List<OrderFact> all) {
        List<OrderFact> orders = live(all);
        List<OrderFact> withDownPayment = orders.stream().filter(o -> o.paid() > 0).toList();
        double totalPaid = sum(withDownPayment, OrderFact::paid);
        List<MethodRow> methods = withDownPayment.stream()
                .collect(Collectors.groupingBy(o -> AnalyticsLabels.paymentMethod(o.paymentMethod())))
                .entrySet().stream()
                .map(e -> {
                    double paid = sum(e.getValue(), OrderFact::paid);
                    return new MethodRow(e.getKey(), e.getValue().size(), money(paid), ratio(paid, totalPaid));
                })
                .sorted(Comparator.comparingDouble(MethodRow::amount).reversed())
                .toList();

        Map<String, List<OrderFact>> byStatus = all.stream()
                .collect(Collectors.groupingBy(o -> o.status() == null ? AnalyticsLabels.UNKNOWN : o.status()));
        List<StatusRow> statuses = byStatus.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> {
                    int i = STATUS_ORDER.indexOf(e.getKey());
                    return i < 0 ? STATUS_ORDER.size() : i;
                }))
                .map(e -> new StatusRow(e.getKey(), e.getValue().size(), money(sum(e.getValue(), OrderFact::orderValue)),
                        money(sum(e.getValue(), OrderFact::outstanding))))
                .toList();

        Map<DayOfWeek, List<OrderFact>> byDay = orders.stream()
                .filter(o -> o.date() != null)
                .collect(Collectors.groupingBy(o -> o.date().getDayOfWeek()));
        List<WeekdayRow> weekdays = new ArrayList<>();
        // The Egyptian week starts on Saturday.
        for (int i = 0; i < 7; i++) {
            DayOfWeek day = DayOfWeek.SATURDAY.plus(i);
            List<OrderFact> os = byDay.getOrDefault(day, List.of());
            weekdays.add(new WeekdayRow(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), day.getValue(),
                    os.size(), money(sum(os, OrderFact::netSales))));
        }

        return new Cash(period, money(sum(all, OrderFact::collected)), money(sum(all, OrderFact::outstanding)),
                methods, statuses, weekdays);
    }

    // --- Helpers ---

    private static List<OrderFact> live(List<OrderFact> orders) {
        return orders.stream().filter(o -> !o.cancelled()).toList();
    }

    /** First order per customer in the list (any one represents them). */
    private static Map<Long, OrderFact> byCustomer(List<OrderFact> orders) {
        Map<Long, OrderFact> map = new LinkedHashMap<>();
        for (OrderFact o : orders) {
            if (o.customerId() != null) map.putIfAbsent(o.customerId(), o);
        }
        return map;
    }

    /** New: their first (non-cancelled) order falls inside the period. */
    private static boolean isNew(OrderFact o, LocalDate from) {
        return o.firstOrder() == null || !o.firstOrder().isBefore(from);
    }

    private static double sum(List<OrderFact> orders, java.util.function.ToDoubleFunction<OrderFact> f) {
        return orders.stream().mapToDouble(f).sum();
    }

    private static double average(List<OrderFact> orders, java.util.function.ToDoubleFunction<OrderFact> f) {
        return orders.isEmpty() ? 0 : sum(orders, f) / orders.size();
    }

    static double ratio(double part, double whole) {
        return whole == 0 ? 0 : round(part / whole, 4);
    }

    static double money(double v) {
        return round(v, 2);
    }

    private static double round(double v, int places) {
        return BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private static Number num(Object v) {
        return v instanceof Number n ? n : 0;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    static LocalDate toDate(Object v) {
        return switch (v) {
            case null -> null;
            case LocalDate d -> d;
            case java.sql.Date d -> d.toLocalDate();
            case java.sql.Timestamp t -> t.toLocalDateTime().toLocalDate();
            case java.time.LocalDateTime t -> t.toLocalDate();
            case java.util.Date d -> new java.sql.Date(d.getTime()).toLocalDate();
            default -> LocalDate.parse(v.toString().substring(0, 10));
        };
    }
}
