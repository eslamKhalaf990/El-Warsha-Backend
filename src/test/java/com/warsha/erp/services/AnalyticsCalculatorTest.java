package com.warsha.erp.services;

import com.warsha.erp.dtos.AnalyticsDtos.*;
import com.warsha.erp.services.AnalyticsCalculator.OrderFact;
import com.warsha.erp.services.AnalyticsCalculator.ProductSale;
import com.warsha.erp.services.AnalyticsCalculator.StockItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);
    private static final Period JANUARY = AnalyticsCalculator.period(FROM, TO, false);

    /** subtotal 400, discount 50, delivery 55, down payment 100 → due 305. */
    private static OrderFact order(long id, String status, long customer, LocalDate firstOrder, String source,
                                   String governorate) {
        return new OrderFact(id, LocalDate.of(2026, 1, 10), status, source, governorate, customer, "C" + customer,
                "010", firstOrder, 1, 400, 150, 2, 50, 55, 305, 100, "vodafone cash");
    }

    private static final OrderFact COMPLETED = order(1, "Completed", 1, LocalDate.of(2025, 12, 1), "tiktok", "القاهرة");
    private static final OrderFact SHIPPED = order(2, "Shipped", 2, FROM, "E-Commerce", "القاهره ");
    private static final OrderFact CANCELLED = order(3, "Cancelled", 3, null, "ecommerce", "الجيزة");

    @Test
    void periodComparesWithTheSameLengthJustBefore() {
        assertThat(JANUARY.days()).isEqualTo(31);
        assertThat(JANUARY.previousFrom()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(JANUARY.previousTo()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(AnalyticsCalculator.period(FROM, TO, true).previousFrom()).isNull();
    }

    @Test
    void kpisRebuildOrderValueBecauseTotalPriceIsOnlyWhatIsDue() {
        Kpis k = AnalyticsCalculator.kpis(List.of(COMPLETED, SHIPPED, CANCELLED), FROM);

        assertThat(k.orders()).isEqualTo(2);
        assertThat(k.cancelledOrders()).isEqualTo(1);
        assertThat(k.cancellationRate()).isEqualTo(0.3333);
        assertThat(k.sales()).isEqualTo(800);
        assertThat(k.netSales()).isEqualTo(700);          // 2 × (400 − 50)
        assertThat(k.orderValue()).isEqualTo(810);        // + 2 × 55 delivery
        assertThat(k.grossProfit()).isEqualTo(400);       // 700 − 2 × 150
        assertThat(k.grossMargin()).isEqualTo(0.5714);
        assertThat(k.averageOrderValue()).isEqualTo(405);
        assertThat(k.itemsPerOrder()).isEqualTo(2);
        // completed: 100 down + 305 final; shipped: 100 down, 305 still due; cancelled counts for nothing
        assertThat(k.collected()).isEqualTo(505);
        assertThat(k.outstanding()).isEqualTo(305);
        assertThat(k.newCustomers()).isEqualTo(1);
        assertThat(k.returningCustomers()).isEqualTo(1);
    }

    @Test
    void emptyPeriodHasZerosNotErrors() {
        Kpis k = AnalyticsCalculator.kpis(List.of(), FROM);
        assertThat(k.orders()).isZero();
        assertThat(k.grossMargin()).isZero();
        assertThat(k.averageOrderValue()).isZero();
    }

    @Test
    void trendFillsEmptyBucketsWithZero() {
        Trend t = AnalyticsCalculator.trend(JANUARY, List.of(COMPLETED, CANCELLED), "day");

        assertThat(t.points()).hasSize(31);
        assertThat(t.points().get(9).orders()).isEqualTo(1);
        assertThat(t.points().get(9).netSales()).isEqualTo(350);
        assertThat(t.points().get(0).orders()).isZero();
    }

    @Test
    void weeksStartOnSaturdayAndGranularityFollowsTheRange() {
        assertThat(AnalyticsCalculator.bucketStart(LocalDate.of(2026, 1, 14), "week")).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(AnalyticsCalculator.granularityFor(JANUARY)).isEqualTo("day");
        assertThat(AnalyticsCalculator.granularityFor(AnalyticsCalculator.period(FROM, FROM.plusDays(120), false))).isEqualTo("week");
        assertThat(AnalyticsCalculator.granularityFor(AnalyticsCalculator.period(FROM, FROM.plusDays(365), false))).isEqualTo("month");
    }

    @Test
    void channelsMergeSpellingsAndCountCancellations() {
        List<ChannelRow> rows = AnalyticsCalculator.channels(List.of(COMPLETED, SHIPPED, CANCELLED));

        assertThat(rows).extracting(ChannelRow::channel).containsExactly("Online store", "TikTok");
        ChannelRow store = rows.getFirst();
        assertThat(store.orders()).isEqualTo(1);
        assertThat(store.cancelled()).isEqualTo(1);
        assertThat(store.cancellationRate()).isEqualTo(0.5);
        assertThat(store.share()).isEqualTo(0.5);
    }

    @Test
    void regionsMergeArabicSpellingsUnderTheMostUsedOne() {
        OrderFact third = order(4, "Completed", 4, FROM, "tiktok", "القاهرة");
        List<RegionRow> rows = AnalyticsCalculator.regions(List.of(COMPLETED, SHIPPED, third, CANCELLED));

        assertThat(rows).hasSize(1);   // Giza only had a cancelled order
        assertThat(rows.getFirst().governorate()).isEqualTo("القاهرة");
        assertThat(rows.getFirst().orders()).isEqualTo(3);
        assertThat(rows.getFirst().customers()).isEqualTo(3);
    }

    @Test
    void productsShowCoverRunningLowAndSlowMovers() {
        List<ProductSale> sales = List.of(
                new ProductSale(1L, "Bracelet", "اسورة", 31, 6200, 200, 1550),
                new ProductSale(2L, "Chain", "سلسله", 10, 2450, 0, 700));
        List<StockItem> stock = List.of(
                new StockItem(1L, "Bracelet", "اسورة", 10, 50, TO),
                new StockItem(2L, "Chain", "سلسله", 60, 70, TO),
                new StockItem(3L, "Ring", "خاتم", 5, 120, LocalDate.of(2025, 11, 2)),
                new StockItem(4L, "Box", "بوكس", 0, 10, null));

        Products p = AnalyticsCalculator.products(JANUARY, sales, stock);

        assertThat(p.categories()).extracting(CategoryRow::category).containsExactly("اسورة", "سلسله");
        assertThat(p.categories().getFirst().grossMargin()).isEqualTo(0.7417);   // (6000 − 1550) / 6000
        ProductRow bracelet = p.topProducts().getFirst();
        assertThat(bracelet.netSales()).isEqualTo(6000);
        assertThat(bracelet.daysOfCover()).isEqualTo(10.0);                     // 10 left at 1 a day
        assertThat(p.runningLow()).extracting(ProductRow::productId).containsExactly(1L);
        assertThat(p.slowMovers()).extracting(SlowMover::productId).containsExactly(3L);
        assertThat(p.slowMovers().getFirst().stockValue()).isEqualTo(600);
    }

    @Test
    void customersCountRepeatersAndRankBySpend() {
        OrderFact repeat = new OrderFact(5, LocalDate.of(2026, 1, 20), "Completed", "tiktok", "الجيزة", 1L, "C1",
                "010", LocalDate.of(2025, 12, 1), 2, 1000, 300, 3, 0, 55, 955, 100, "cash");
        OrderFact first = new OrderFact(1, LocalDate.of(2026, 1, 10), "Completed", "tiktok", "الجيزة", 1L, "C1",
                "010", LocalDate.of(2025, 12, 1), 2, 400, 150, 2, 50, 55, 305, 100, "cash");

        Customers c = AnalyticsCalculator.customers(JANUARY, List.of(first, repeat, SHIPPED, CANCELLED));

        assertThat(c.customers()).isEqualTo(2);
        assertThat(c.newCustomers()).isEqualTo(1);
        assertThat(c.repeatRate()).isEqualTo(0.5);
        CustomerRow top = c.topCustomers().getFirst();
        assertThat(top.customerId()).isEqualTo(1L);
        assertThat(top.orders()).isEqualTo(2);
        assertThat(top.spend()).isEqualTo(1460);   // 405 + 1055
        assertThat(top.lastOrder()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    @Test
    void cashSplitsMethodsStatusesAndWeekdays() {
        OrderFact cashOrder = new OrderFact(6, LocalDate.of(2026, 1, 16), "Pending", "", null, 9L, "C9", null,
                FROM, 1, 200, 50, 1, 0, 0, 150, 50, "Cash");

        Cash c = AnalyticsCalculator.cash(JANUARY, List.of(COMPLETED, SHIPPED, CANCELLED, cashOrder));

        assertThat(c.paymentMethods()).extracting(MethodRow::method).containsExactly("Vodafone Cash", "Cash");
        assertThat(c.paymentMethods().getFirst().share()).isEqualTo(0.8);
        assertThat(c.statuses()).extracting(StatusRow::status)
                .containsExactly("Pending", "Shipped", "Completed", "Cancelled");
        assertThat(c.outstanding()).isEqualTo(455);   // shipped 305 + pending 150
        assertThat(c.weekdays()).hasSize(7);
        assertThat(c.weekdays().getFirst().weekday()).isEqualTo("Saturday");
        // 10 Jan 2026 is a Saturday, 16 Jan a Friday
        assertThat(c.weekdays().getFirst().orders()).isEqualTo(2);
        assertThat(c.weekdays().get(6).orders()).isEqualTo(1);
    }

    @Test
    void stockTextThatIsNotANumberCountsAsZero() {
        StockItem item = StockItem.fromRow(new Object[]{7, "Gift", null, "n/a", 12.5, null});
        assertThat(item.stock()).isZero();
        assertThat(StockItem.fromRow(new Object[]{7, "Gift", null, "12", 12.5, null}).stock()).isEqualTo(12);
    }
}
