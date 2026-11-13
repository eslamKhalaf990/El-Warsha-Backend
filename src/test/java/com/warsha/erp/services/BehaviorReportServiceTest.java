package com.warsha.erp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.BehaviorDtos.*;
import com.warsha.erp.entities.Product;
import com.warsha.erp.entities.ProductVariant;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.BehaviorRepository;
import com.warsha.erp.repository.ProductRepository;
import com.warsha.erp.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BehaviorReportServiceTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 10, 31, 12, 0).atZone(CAIRO).toInstant(), CAIRO);

    private final BehaviorRepository repository = mock(BehaviorRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final BehaviorReportService service =
            new BehaviorReportService(repository, products, variants, new ObjectMapper(), CLOCK);

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    @Test
    void defaultsToTheLast30DaysAndChecksTheRange() {
        Range r = service.range(null, null);
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(r.days()).isEqualTo(30);

        assertThatThrownBy(() -> service.range(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.range(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 31)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("one year");
    }

    @Test
    void overviewWorksOutTheFunnelAndQueriesWholeDays() {
        when(repository.totals(any(), any())).thenReturn(rows(new Object[]{120, 150, 600, 400, 80, 20, 30, 12, new BigDecimal("4200.50")}));
        when(repository.funnel(any(), any())).thenReturn(rows(new Object[]{150, 100, 40, 25, 10}));
        when(repository.newVisitors(any(), any())).thenReturn(70L);
        when(repository.devices(any(), any())).thenReturn(rows(new Object[]{"DESKTOP", 30}, new Object[]{"MOBILE", 120}));
        when(repository.days(any(), any())).thenReturn(rows(new Object[]{java.sql.Date.valueOf("2026-10-30"), 50, 60, 200, 30, 5}));

        Overview o = service.overview(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30));

        verify(repository).totals(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 31, 0, 0));
        assertThat(o.visitors()).isEqualTo(120);
        assertThat(o.newVisitors()).isEqualTo(70);
        assertThat(o.orders()).isEqualTo(12);
        assertThat(o.orderValue()).isEqualTo(4200.50);
        Funnel f = o.funnel();
        assertThat(f.viewToCartRate()).isEqualTo(0.4);
        assertThat(f.cartToCheckoutRate()).isEqualTo(0.625);
        assertThat(f.checkoutToOrderRate()).isEqualTo(0.4);
        assertThat(f.conversionRate()).isEqualTo(0.0667);
        assertThat(o.devices()).extracting(DeviceRow::device).containsExactly("MOBILE", "DESKTOP");
        assertThat(o.devices().get(0).share()).isEqualTo(0.8);
        assertThat(o.days().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void overviewWithNoEventsIsAllZeros() {
        when(repository.totals(any(), any())).thenReturn(rows(new Object[]{0, 0, null, null, null, null, null, null, null}));
        when(repository.funnel(any(), any())).thenReturn(rows(new Object[]{0, 0, 0, 0, 0}));

        Overview o = service.overview(null, null);

        assertThat(o.pageViews()).isZero();
        assertThat(o.orderValue()).isZero();
        assertThat(o.funnel().conversionRate()).isZero();
        assertThat(o.devices()).isEmpty();
    }

    @Test
    void productsMergeInterestWithOrdersAndSortByViews() {
        when(repository.productInterest(any(), any())).thenReturn(rows(
                new Object[]{1L, "Mug", 10, 8, 4, 1},
                new Object[]{2L, "Shirt", 50, 30, 5, 3},
                new Object[]{9L, null, 2, 2, 0, 0}));
        when(repository.productOrders(any(), any())).thenReturn(rows(
                new Object[]{1L, "Mug", 2, 3},
                new Object[]{7L, "Tote", 1, 1}));

        List<ProductRow> list = service.products(null, null);

        assertThat(list).extracting(ProductRow::productId).containsExactly(2L, 1L, 9L, 7L);
        ProductRow mug = list.get(1);
        assertThat(mug.viewToCartRate()).isEqualTo(0.4);
        assertThat(mug.orders()).isEqualTo(2);
        assertThat(mug.unitsOrdered()).isEqualTo(3);
        assertThat(mug.cartToOrderRate()).isEqualTo(0.5);
        assertThat(list.get(2).name()).isEqualTo("Deleted product #9");
        assertThat(list.get(3).views()).isZero();
    }

    @Test
    void abandonedCartsNameTheItemsAndTheCustomer() {
        when(repository.abandonedCarts(any(), any(), any())).thenReturn(rows(
                new Object[]{"visitor-0001", 44, "Mona Ali", "01000000000", "mona@mail.com",
                        "[{\"productId\":1,\"variantId\":9,\"quantity\":2},{\"productId\":3,\"variantId\":null,\"quantity\":1}]",
                        3, new BigDecimal("430.00"), Timestamp.valueOf("2026-10-31 09:15:00"),
                        null, "https://l.facebook.com/", "MOBILE"},
                new Object[]{"visitor-0002", null, null, null, null, "not json", 1, 50, Timestamp.valueOf("2026-10-30 20:00:00"),
                        "instagram", null, "DESKTOP"}));
        when(repository.abandonedTotals(any(), any(), any())).thenReturn(rows(new Object[]{2, new BigDecimal("480.00")}));
        when(repository.orderedCarts(any(), any())).thenReturn(6L);
        Product mug = new Product();
        mug.setProductID(1L);
        mug.setName("Mug");
        ProductVariant large = new ProductVariant();
        large.setId(9L);
        large.setSize("L");
        large.setColor("Black");
        when(products.findAllById(any())).thenReturn(List.of(mug));
        when(variants.findAllById(any())).thenReturn(List.of(large));

        AbandonedCarts result = service.abandonedCarts(null, null, null);

        verify(repository).abandonedCarts(any(), any(), eq(LocalDateTime.of(2026, 10, 31, 11, 0)));
        assertThat(result.idleMinutes()).isEqualTo(60);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.value()).isEqualTo(480.0);
        assertThat(result.abandonmentRate()).isEqualTo(0.25);
        AbandonedCart mona = result.carts().get(0);
        assertThat(mona.customerName()).isEqualTo("Mona Ali");
        assertThat(mona.customerId()).isEqualTo(44L);
        assertThat(mona.source()).isEqualTo("Facebook");
        assertThat(mona.lastActivity()).isEqualTo(LocalDateTime.of(2026, 10, 31, 9, 15));
        assertThat(mona.items()).extracting(CartItem::name).containsExactly("Mug", "Deleted product #3");
        assertThat(mona.items().get(0).variant()).isEqualTo("L / Black");
        assertThat(mona.items().get(0).quantity()).isEqualTo(2);
        AbandonedCart guest = result.carts().get(1);
        assertThat(guest.customerId()).isNull();
        assertThat(guest.items()).isEmpty();
        assertThat(guest.source()).isEqualTo("Instagram");
    }

    @Test
    void abandonedCartsCheckTheIdleTime() {
        assertThatThrownBy(() -> service.abandonedCarts(null, null, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idle time");
    }

    @Test
    void searchesListTopAndEmptyResults() {
        when(repository.searches(any(), any())).thenReturn(rows(
                new Object[]{"mug", 20, 15, 4.333, 0},
                new Object[]{"tote bag", 5, 5, 0.0, 5},
                new Object[]{"red", 8, 6, null, 2}));

        Searches s = service.searches(null, null);

        assertThat(s.total()).isEqualTo(33);
        assertThat(s.noResults()).isEqualTo(7);
        assertThat(s.top()).extracting(SearchRow::query).containsExactly("mug", "red", "tote bag");
        assertThat(s.top().get(0).averageResults()).isEqualTo(4.3);
        assertThat(s.top().get(1).averageResults()).isNull();
        assertThat(s.withoutResults()).extracting(SearchRow::query).containsExactly("tote bag", "red");
    }

    @Test
    void vouchersGroupResultsPerCode() {
        when(repository.vouchers(any(), any())).thenReturn(rows(
                new Object[]{"RAMADAN10", "APPLIED", 6, 5},
                new Object[]{"RAMADAN10", "EXPIRED", 3, 2},
                new Object[]{"SAVE5", "NOT_FOUND", 1, 1},
                new Object[]{"RAMADAN10", "MIN_ORDER", 1, 1}));

        List<VoucherRow> list = service.vouchers(null, null);

        assertThat(list).extracting(VoucherRow::code).containsExactly("RAMADAN10", "SAVE5");
        VoucherRow ramadan = list.get(0);
        assertThat(ramadan.tries()).isEqualTo(10);
        assertThat(ramadan.applied()).isEqualTo(6);
        assertThat(ramadan.rejected()).isEqualTo(4);
        assertThat(ramadan.rejections()).isEqualTo(Map.of("EXPIRED", 3L, "MIN_ORDER", 1L));
    }

    @Test
    void sourcesMergeTheSameSite() {
        when(repository.sources(any(), any())).thenReturn(rows(
                new Object[]{null, "https://m.facebook.com/", 30, 3, 3, new BigDecimal("900")},
                new Object[]{"facebook", null, 10, 1, 2, new BigDecimal("300")},
                new Object[]{null, null, 50, 5, 6, new BigDecimal("1500")},
                new Object[]{null, "https://www.example.com/blog", 2, 0, 0, BigDecimal.ZERO}));

        List<SourceRow> list = service.sources(null, null);

        assertThat(list).extracting(SourceRow::source).containsExactly("Direct", "Facebook", "example.com");
        SourceRow facebook = list.get(1);
        assertThat(facebook.visitors()).isEqualTo(40);
        assertThat(facebook.orders()).isEqualTo(5);
        assertThat(facebook.orderValue()).isEqualTo(1200.0);
        assertThat(facebook.conversionRate()).isEqualTo(0.1);
    }

    @Test
    void namesTrafficSources() {
        assertThat(BehaviorReportService.source("IG", null)).isEqualTo("Instagram");
        assertThat(BehaviorReportService.source("newsletter", "https://google.com")).isEqualTo("newsletter");
        assertThat(BehaviorReportService.source(" ", "https://www.google.com.eg/")).isEqualTo("Google");
        assertThat(BehaviorReportService.source(null, "android-app://com.whatsapp/")).isEqualTo("WhatsApp");
        assertThat(BehaviorReportService.source(null, "not a url")).isEqualTo("Direct");
    }
}
