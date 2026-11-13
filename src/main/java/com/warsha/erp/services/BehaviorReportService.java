package com.warsha.erp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.BehaviorDtos.*;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.BehaviorRepository;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Store visitor behavior reports for the ERP, built from the events written by
 * {@link StoreTrackingService}. Every report takes an optional date range: no {@code to}
 * is today, no {@code from} is the 30 days ending on {@code to}; at most one year.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BehaviorReportService {

    static final int DEFAULT_DAYS = 30;
    static final long MAX_DAYS = 366;
    static final int DEFAULT_IDLE_MINUTES = 60;
    static final int MIN_IDLE_MINUTES = 15;
    static final int MAX_IDLE_MINUTES = 7 * 24 * 60;
    static final int TOP_ROWS = 100;

    private static final String DIRECT = "Direct";
    private static final String APPLIED = "APPLIED";

    private final BehaviorRepository repository;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public BehaviorReportService(BehaviorRepository repository, ProductRepository products,
                                 ProductVariantRepository variants, ObjectMapper json) {
        this(repository, products, variants, json, Clock.systemDefaultZone());
    }

    BehaviorReportService(BehaviorRepository repository, ProductRepository products,
                          ProductVariantRepository variants, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.products = products;
        this.variants = variants;
        this.json = json;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- overview

    public Overview overview(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        LocalDateTime start = start(r);
        LocalDateTime end = end(r);

        Object[] t = first(repository.totals(start, end), 9);
        Object[] f = first(repository.funnel(start, end), 5);
        long sessions = num(f[0]);
        long viewed = num(f[1]);
        long added = num(f[2]);
        long checkout = num(f[3]);
        long ordered = num(f[4]);
        Funnel funnel = new Funnel(sessions, viewed, added, checkout, ordered,
                rate(added, viewed), rate(checkout, added), rate(ordered, checkout), rate(ordered, sessions));

        List<Object[]> deviceRows = repository.devices(start, end);
        long deviceSessions = deviceRows.stream().mapToLong(d -> num(d[1])).sum();
        List<DeviceRow> devices = deviceRows.stream()
                .map(d -> new DeviceRow(d[0] == null ? "UNKNOWN" : d[0].toString(), num(d[1]), rate(num(d[1]), deviceSessions)))
                .sorted(Comparator.comparingLong(DeviceRow::sessions).reversed())
                .toList();

        List<DayRow> days = repository.days(start, end).stream()
                .map(d -> new DayRow(AnalyticsCalculator.toDate(d[0]), num(d[1]), num(d[2]), num(d[3]), num(d[4]), num(d[5])))
                .toList();

        return new Overview(r, num(t[0]), repository.newVisitors(start, end), num(t[1]), num(t[2]), num(t[3]),
                num(t[4]), num(t[5]), num(t[6]), num(t[7]), money(t[8]), funnel, devices, days);
    }

    // ---------------------------------------------------------------- products

    /** The {@value #TOP_ROWS} products with the most interest (views, then cart adds, then orders). */
    public List<ProductRow> products(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        Map<Long, long[]> interest = new HashMap<>();   // views, viewers, adds, removes
        Map<Long, long[]> ordered = new HashMap<>();    // orders, units
        Map<Long, String> names = new HashMap<>();
        for (Object[] row : repository.productInterest(start(r), end(r))) {
            long id = num(row[0]);
            interest.put(id, new long[]{num(row[2]), num(row[3]), num(row[4]), num(row[5])});
            putName(names, id, row[1]);
        }
        for (Object[] row : repository.productOrders(start(r), end(r))) {
            long id = num(row[0]);
            ordered.put(id, new long[]{num(row[2]), num(row[3])});
            putName(names, id, row[1]);
        }
        return Stream.concat(interest.keySet().stream(), ordered.keySet().stream()).distinct()
                .map(id -> {
                    long[] i = interest.getOrDefault(id, new long[4]);
                    long[] o = ordered.getOrDefault(id, new long[2]);
                    return new ProductRow(id, names.getOrDefault(id, "Deleted product #" + id),
                            i[0], i[1], i[2], i[3], o[0], o[1], rate(i[2], i[0]), rate(o[0], i[2]));
                })
                .sorted(Comparator.comparingLong(ProductRow::views)
                        .thenComparingLong(ProductRow::cartAdds)
                        .thenComparingLong(ProductRow::orders).reversed()
                        .thenComparingLong(ProductRow::productId))
                .limit(TOP_ROWS)
                .toList();
    }

    // ------------------------------------------------------------------- carts

    public AbandonedCarts abandonedCarts(LocalDate from, LocalDate to, Integer idleMinutes) {
        Range r = range(from, to);
        int idle = idleMinutes != null ? idleMinutes : DEFAULT_IDLE_MINUTES;
        if (idle < MIN_IDLE_MINUTES || idle > MAX_IDLE_MINUTES) {
            throw new ValidationException("idleMinutes",
                    "Idle time must be between " + MIN_IDLE_MINUTES + " minutes and 7 days.");
        }
        LocalDateTime idleBefore = LocalDateTime.now(clock).minusMinutes(idle);
        List<Object[]> rows = repository.abandonedCarts(start(r), end(r), idleBefore);
        Object[] totals = first(repository.abandonedTotals(start(r), end(r), idleBefore), 2);
        long count = num(totals[0]);
        long orderedCarts = repository.orderedCarts(start(r), end(r));

        Map<String, List<Line>> lines = new LinkedHashMap<>();
        for (Object[] row : rows) {
            lines.put(row[0].toString(), parse(row[5]));
        }
        Set<Long> productIds = lines.values().stream().flatMap(List::stream).map(Line::productId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> variantIds = lines.values().stream().flatMap(List::stream).map(Line::variantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> productById = productIds.isEmpty() ? Map.of() : products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductID, Function.identity()));
        Map<Long, ProductVariant> variantById = variantIds.isEmpty() ? Map.of() : variants.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<AbandonedCart> carts = rows.stream().map(row -> {
            List<CartItem> items = lines.get(row[0].toString()).stream()
                    .filter(l -> l.productId() != null)
                    .map(l -> {
                        Product p = productById.get(l.productId());
                        ProductVariant v = l.variantId() != null ? variantById.get(l.variantId()) : null;
                        return new CartItem(l.productId(), l.variantId(),
                                p != null ? p.getName() : "Deleted product #" + l.productId(),
                                variantLabel(v), l.quantity() != null ? l.quantity() : 0);
                    })
                    .toList();
            return new AbandonedCart(row[0].toString(), row[1] == null ? null : num(row[1]),
                    str(row[2]), str(row[3]), str(row[4]), (int) num(row[6]), money(row[7]),
                    dateTime(row[8]), source(str(row[9]), str(row[10])), str(row[11]), items);
        }).toList();

        return new AbandonedCarts(r, idle, count, money(totals[1]), orderedCarts,
                rate(count, count + orderedCarts), carts);
    }

    // ---------------------------------------------------------------- searches

    public Searches searches(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        List<SearchRow> rows = repository.searches(start(r), end(r)).stream()
                .map(s -> new SearchRow(str(s[0]), num(s[1]), num(s[2]),
                        s[3] == null ? null : round(((Number) s[3]).doubleValue(), 1), num(s[4])))
                .toList();
        Comparator<SearchRow> bySearches = Comparator.comparingLong(SearchRow::searches).reversed()
                .thenComparing(SearchRow::query);
        return new Searches(
                rows.stream().mapToLong(SearchRow::searches).sum(),
                rows.stream().mapToLong(SearchRow::noResultSearches).sum(),
                rows.stream().sorted(bySearches).limit(TOP_ROWS).toList(),
                rows.stream().filter(s -> s.noResultSearches() > 0)
                        .sorted(Comparator.comparingLong(SearchRow::noResultSearches).reversed()
                                .thenComparing(SearchRow::query))
                        .limit(TOP_ROWS).toList());
    }

    // ---------------------------------------------------------------- vouchers

    /** Codes customers typed, most tried first; {@code visitors} may count one visitor per result. */
    public List<VoucherRow> vouchers(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        Map<String, List<Object[]>> byCode = repository.vouchers(start(r), end(r)).stream()
                .collect(Collectors.groupingBy(v -> v[0].toString(), LinkedHashMap::new, Collectors.toList()));
        return byCode.entrySet().stream().map(e -> {
            long tries = 0, applied = 0, visitors = 0;
            Map<String, Long> rejections = new TreeMap<>();
            for (Object[] row : e.getValue()) {
                String result = row[1] == null ? "UNKNOWN" : row[1].toString();
                long n = num(row[2]);
                tries += n;
                visitors += num(row[3]);
                if (APPLIED.equals(result)) {
                    applied += n;
                } else {
                    rejections.merge(result, n, Long::sum);
                }
            }
            return new VoucherRow(e.getKey(), tries, visitors, applied, tries - applied, rejections);
        }).sorted(Comparator.comparingLong(VoucherRow::tries).reversed().thenComparing(VoucherRow::code)).toList();
    }

    // ----------------------------------------------------------------- sources

    public List<SourceRow> sources(LocalDate from, LocalDate to) {
        Range r = range(from, to);
        Map<String, long[]> counts = new LinkedHashMap<>();   // visitors, orderingVisitors, orders
        Map<String, BigDecimal> values = new HashMap<>();
        for (Object[] row : repository.sources(start(r), end(r))) {
            String source = source(str(row[0]), str(row[1]));
            long[] c = counts.computeIfAbsent(source, k -> new long[3]);
            c[0] += num(row[2]);
            c[1] += num(row[3]);
            c[2] += num(row[4]);
            values.merge(source, decimal(row[5]), BigDecimal::add);
        }
        return counts.entrySet().stream()
                .map(e -> new SourceRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        values.get(e.getKey()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                        rate(e.getValue()[1], e.getValue()[0])))
                .sorted(Comparator.comparingLong(SourceRow::visitors).reversed().thenComparing(SourceRow::source))
                .toList();
    }

    /**
     * A readable traffic source: the utm_source when the link had one, else the referring
     * site, else Direct. Common social sites are merged (m.facebook.com, l.facebook.com…).
     */
    static String source(String utmSource, String referrer) {
        String utm = utmSource != null ? utmSource.trim() : "";
        if (!utm.isEmpty()) {
            String known = knownSite(utm.toLowerCase(Locale.ROOT));
            return known != null ? known : utm.toLowerCase(Locale.ROOT);
        }
        if (referrer == null || referrer.isBlank()) {
            return DIRECT;
        }
        String host;
        try {
            host = URI.create(referrer.trim()).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null) {
            return DIRECT;
        }
        host = host.toLowerCase(Locale.ROOT);
        String known = knownSite(host);
        return known != null ? known : host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String knownSite(String value) {
        if (value.equals("fb") || value.contains("facebook") || value.equals("fb.com") || value.endsWith(".fb.com")) return "Facebook";
        if (value.equals("ig") || value.contains("instagram")) return "Instagram";
        if (value.contains("google")) return "Google";
        if (value.contains("tiktok")) return "TikTok";
        if (value.contains("whatsapp") || value.equals("wa.me") || value.equals("wa")) return "WhatsApp";
        if (value.equals("t.co") || value.contains("twitter") || value.equals("x.com")) return "X";
        return null;
    }

    // ----------------------------------------------------------------- helpers

    Range range(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        if (start.isAfter(end)) {
            throw new ValidationException("from", "The start date must be on or before the end date.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) {
            throw new ValidationException("from", "Choose a date range of at most one year.");
        }
        return new Range(start, end, days);
    }

    private static LocalDateTime start(Range r) {
        return r.from().atStartOfDay();
    }

    private static LocalDateTime end(Range r) {
        return r.to().plusDays(1).atStartOfDay();
    }

    record Line(Long productId, Long variantId, Integer quantity) {
    }

    private List<Line> parse(Object items) {
        if (items == null) {
            return List.of();
        }
        try {
            return json.readValue(items.toString(), new TypeReference<List<Line>>() {
            });
        } catch (Exception e) {
            log.warn("Unreadable StoreCarts.Items: {}", e.getMessage());
            return List.of();
        }
    }

    private static String variantLabel(ProductVariant v) {
        if (v == null) {
            return null;
        }
        String label = Stream.of(v.getSize(), v.getColor()).filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" / "));
        return label.isEmpty() ? null : label;
    }

    private static void putName(Map<Long, String> names, long id, Object name) {
        if (name != null) {
            names.putIfAbsent(id, name.toString());
        }
    }

    /** The single row of an aggregate query, or zeros when the driver returns none. */
    private static Object[] first(List<Object[]> rows, int width) {
        return rows.isEmpty() || rows.get(0) == null ? new Object[width] : rows.get(0);
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }

    private static BigDecimal decimal(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static double money(Object v) {
        return decimal(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static LocalDateTime dateTime(Object v) {
        return switch (v) {
            case null -> null;
            case LocalDateTime t -> t;
            case java.sql.Timestamp t -> t.toLocalDateTime();
            default -> LocalDateTime.parse(v.toString().replace(' ', 'T'));
        };
    }

    static double rate(long part, long whole) {
        return whole == 0 ? 0 : round((double) part / whole, 4);
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
