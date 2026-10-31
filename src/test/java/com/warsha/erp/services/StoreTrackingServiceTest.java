package com.warsha.erp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.TrackEventsRequest;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreTrackingServiceTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, CAIRO);

    private final StoreEventRepository events = mock(StoreEventRepository.class);
    private final StoreVisitorRepository visitors = mock(StoreVisitorRepository.class);
    private final StoreCartRepository carts = mock(StoreCartRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final TrackingRateLimiter limiter = mock(TrackingRateLimiter.class);
    private final StoreTrackingService service = new StoreTrackingService(
            events, visitors, carts, products, variants, limiter, new ObjectMapper());

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(NOW, CAIRO));
        when(limiter.allow(any(), any())).thenReturn(true);
        when(visitors.findById(any())).thenReturn(Optional.empty());
        when(carts.findById(any())).thenReturn(Optional.empty());
    }

    private static TrackEventsRequest request(TrackEventsRequest.Event... events) {
        TrackEventsRequest r = new TrackEventsRequest();
        r.setVisitorId("visitor-0001");
        r.setSessionId("session-0001");
        r.setDeviceType("mobile");
        r.setLandingPage("/product/5");
        r.setReferrer("https://instagram.com/");
        r.setUtmSource("instagram");
        r.setEvents(List.of(events));
        return r;
    }

    private static TrackEventsRequest.Event event(StoreEventType type) {
        TrackEventsRequest.Event e = new TrackEventsRequest.Event();
        e.setType(type);
        return e;
    }

    private static TrackEventsRequest.CartLine line(long productId, Long variantId, int quantity) {
        TrackEventsRequest.CartLine l = new TrackEventsRequest.CartLine();
        l.setProductId(productId);
        l.setVariantId(variantId);
        l.setQuantity(quantity);
        return l;
    }

    private static TrackEventsRequest.Cart cart(TrackEventsRequest.CartLine... lines) {
        TrackEventsRequest.Cart c = new TrackEventsRequest.Cart();
        c.setItems(List.of(lines));
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<StoreEvent> savedEvents() {
        ArgumentCaptor<List<StoreEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).saveAll(captor.capture());
        return captor.getValue();
    }

    private StoreCart savedCart() {
        ArgumentCaptor<StoreCart> captor = ArgumentCaptor.forClass(StoreCart.class);
        verify(carts).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesEventsWithTheBatchContext() {
        TrackEventsRequest.Event view = event(StoreEventType.PRODUCT_VIEW);
        view.setProductId(5L);
        view.setPath("/product/5");

        service.track(request(view), null, "Mozilla/5.0 (iPhone)", "1.1.1.1");

        StoreEvent row = savedEvents().get(0);
        assertThat(row.getType()).isEqualTo(StoreEventType.PRODUCT_VIEW);
        assertThat(row.getVisitorId()).isEqualTo("visitor-0001");
        assertThat(row.getSessionId()).isEqualTo("session-0001");
        assertThat(row.getProductId()).isEqualTo(5L);
        assertThat(row.getPagePath()).isEqualTo("/product/5");
        assertThat(row.getUtmSource()).isEqualTo("instagram");
        assertThat(row.getDeviceType()).isEqualTo("MOBILE");
        assertThat(row.getCustomerId()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(LOCAL_NOW);
    }

    @Test
    void createsTheVisitorWithWhereTheyCameFrom() {
        service.track(request(event(StoreEventType.PAGE_VIEW)), null, null, null);

        ArgumentCaptor<StoreVisitor> captor = ArgumentCaptor.forClass(StoreVisitor.class);
        verify(visitors).save(captor.capture());
        StoreVisitor v = captor.getValue();
        assertThat(v.getFirstSeenAt()).isEqualTo(LOCAL_NOW);
        assertThat(v.getFirstLandingPage()).isEqualTo("/product/5");
        assertThat(v.getFirstReferrer()).isEqualTo("https://instagram.com/");
        assertThat(v.getDeviceType()).isEqualTo("MOBILE");
    }

    @Test
    void keepsPlausibleBrowserTimesAndClampsOthers() {
        TrackEventsRequest.Event recent = event(StoreEventType.PAGE_VIEW);
        recent.setOccurredAt(NOW.minusSeconds(30));
        TrackEventsRequest.Event future = event(StoreEventType.PAGE_VIEW);
        future.setOccurredAt(NOW.plusSeconds(3600));
        TrackEventsRequest.Event ancient = event(StoreEventType.PAGE_VIEW);
        ancient.setOccurredAt(NOW.minus(Duration.ofDays(3)));

        service.track(request(recent, future, ancient), null, null, null);

        List<StoreEvent> rows = savedEvents();
        assertThat(rows.get(0).getOccurredAt()).isEqualTo(LOCAL_NOW.minusSeconds(30));
        assertThat(rows.get(1).getOccurredAt()).isEqualTo(LOCAL_NOW);
        assertThat(rows.get(2).getOccurredAt()).isEqualTo(LOCAL_NOW);
    }

    @Test
    void cutsLongTextAndNormalizesVoucherCodes() {
        TrackEventsRequest.Event search = event(StoreEventType.SEARCH);
        search.setSearchQuery("  " + "x".repeat(150) + "  ");
        TrackEventsRequest.Event voucher = event(StoreEventType.VOUCHER_APPLY);
        voucher.setVoucherCode(" ramadan10 ");
        voucher.setVoucherResult("EXPIRED");
        TrackEventsRequest r = request(search, voucher);
        r.setDeviceType("fridge");

        service.track(r, null, null, null);

        List<StoreEvent> rows = savedEvents();
        assertThat(rows.get(0).getSearchQuery()).hasSize(100);
        assertThat(rows.get(1).getVoucherCode()).isEqualTo("RAMADAN10");
        assertThat(rows.get(1).getVoucherResult()).isEqualTo("EXPIRED");
        assertThat(rows.get(0).getDeviceType()).isNull();
    }

    @Test
    void dropsBotsAndRateLimitedBatches() {
        service.track(request(event(StoreEventType.PAGE_VIEW)), null, "Googlebot/2.1", null);
        when(limiter.allow(any(), any())).thenReturn(false);
        service.track(request(event(StoreEventType.PAGE_VIEW)), null, "Mozilla/5.0", null);

        verifyNoInteractions(events, visitors, carts);
    }

    @Test
    void refusesOrderPlacedFromTheStore() {
        assertThatThrownBy(() -> service.track(request(event(StoreEventType.ORDER_PLACED)), null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("recorded by the server");
        verifyNoInteractions(events);
    }

    @Test
    void firstLoginGivesEarlierGuestEventsToTheCustomer() {
        StoreVisitor guest = new StoreVisitor();
        guest.setId("visitor-0001");
        when(visitors.findById("visitor-0001")).thenReturn(Optional.of(guest));

        service.track(request(event(StoreEventType.PAGE_VIEW)), 44L, null, null);

        assertThat(guest.getCustomerId()).isEqualTo(44L);
        verify(events).assignCustomer("visitor-0001", 44L);
        assertThat(savedEvents().get(0).getCustomerId()).isEqualTo(44L);
    }

    @Test
    void knownCustomerDoesNotReassignAgain() {
        StoreVisitor known = new StoreVisitor();
        known.setId("visitor-0001");
        known.setCustomerId(44L);
        when(visitors.findById("visitor-0001")).thenReturn(Optional.of(known));

        service.track(request(event(StoreEventType.PAGE_VIEW)), 44L, null, null);

        verify(events, never()).assignCustomer(any(), any());
    }

    @Test
    void pricesTheCartOnTheServer() {
        Product mug = new Product();
        mug.setProductID(1L);
        mug.setSellingPrice(100.0);
        mug.setDiscount(10.0);
        Product shirt = new Product();
        shirt.setProductID(2L);
        shirt.setSellingPrice(200.0);
        shirt.setDiscount(0.0);
        ProductVariant large = new ProductVariant();
        large.setId(9L);
        large.setSellingPrice(250.0);
        when(products.findAllById(any())).thenReturn(List.of(mug, shirt));
        when(variants.findAllById(any())).thenReturn(List.of(large));

        TrackEventsRequest r = request(event(StoreEventType.CART_ADD));
        // 2 × (100 − 10) + 1 × 250 (variant price); product 3 was deleted and is ignored
        r.setCart(cart(line(1, null, 2), line(2, 9L, 1), line(3, null, 1)));
        service.track(r, null, null, null);

        StoreCart saved = savedCart();
        assertThat(saved.getCartValue()).isEqualByComparingTo("430.00");
        assertThat(saved.getItemCount()).isEqualTo(4);
        assertThat(saved.getStatus()).isEqualTo(StoreCartStatus.ACTIVE);
        assertThat(saved.getUpdatedAt()).isEqualTo(LOCAL_NOW);
        assertThat(saved.getItems()).isEqualTo(
                "[{\"productId\":1,\"variantId\":null,\"quantity\":2},"
                        + "{\"productId\":2,\"variantId\":9,\"quantity\":1},"
                        + "{\"productId\":3,\"variantId\":null,\"quantity\":1}]");
    }

    @Test
    void emptiedCartIsEmpty() {
        TrackEventsRequest r = request(event(StoreEventType.CART_REMOVE));
        r.setCart(cart());
        service.track(r, null, null, null);

        StoreCart saved = savedCart();
        assertThat(saved.getStatus()).isEqualTo(StoreCartStatus.EMPTY);
        assertThat(saved.getCartValue()).isEqualByComparingTo("0");
    }

    @Test
    void clearingTheCartAfterCheckoutKeepsItOrdered() {
        StoreCart ordered = new StoreCart();
        ordered.setVisitorId("visitor-0001");
        ordered.setStatus(StoreCartStatus.ORDERED);
        ordered.setOrderId(12L);
        when(carts.findById("visitor-0001")).thenReturn(Optional.of(ordered));

        TrackEventsRequest r = request();
        r.setCart(cart());
        service.track(r, null, null, null);

        verify(carts, never()).save(any());
        assertThat(ordered.getOrderId()).isEqualTo(12L);
    }

    @Test
    void newItemsAfterAnOrderStartAFreshCart() {
        StoreCart ordered = new StoreCart();
        ordered.setVisitorId("visitor-0001");
        ordered.setStatus(StoreCartStatus.ORDERED);
        ordered.setOrderId(12L);
        when(carts.findById("visitor-0001")).thenReturn(Optional.of(ordered));
        when(products.findAllById(any())).thenReturn(List.of());

        TrackEventsRequest r = request(event(StoreEventType.CART_ADD));
        r.setCart(cart(line(1, null, 1)));
        service.track(r, null, null, null);

        assertThat(savedCart().getStatus()).isEqualTo(StoreCartStatus.ACTIVE);
        assertThat(ordered.getOrderId()).isNull();
    }

    @Test
    void recordOrderLogsTheOrderAndClosesTheCustomersCarts() {
        StoreCart thisBrowser = new StoreCart();
        thisBrowser.setVisitorId("visitor-0001");
        thisBrowser.setStatus(StoreCartStatus.ACTIVE);
        StoreCart otherDevice = new StoreCart();
        otherDevice.setVisitorId("visitor-0002");
        otherDevice.setStatus(StoreCartStatus.ACTIVE);
        when(carts.findById("visitor-0001")).thenReturn(Optional.of(thisBrowser));
        when(carts.findByCustomerIdAndStatus(44L, StoreCartStatus.ACTIVE)).thenReturn(List.of(thisBrowser, otherDevice));

        service.recordOrder("visitor-0001", "session-0001", 44L, 12L, new BigDecimal("350.00"));

        ArgumentCaptor<StoreEvent> captor = ArgumentCaptor.forClass(StoreEvent.class);
        verify(events).save(captor.capture());
        StoreEvent row = captor.getValue();
        assertThat(row.getType()).isEqualTo(StoreEventType.ORDER_PLACED);
        assertThat(row.getOrderId()).isEqualTo(12L);
        assertThat(row.getOrderValue()).isEqualByComparingTo("350.00");
        assertThat(row.getCustomerId()).isEqualTo(44L);
        for (StoreCart c : List.of(thisBrowser, otherDevice)) {
            assertThat(c.getStatus()).isEqualTo(StoreCartStatus.ORDERED);
            assertThat(c.getOrderId()).isEqualTo(12L);
            assertThat(c.getOrderedAt()).isEqualTo(LOCAL_NOW);
        }
    }

    @Test
    void recordOrderFromAnOldStoreBuildStillClosesCarts() {
        StoreCart open = new StoreCart();
        open.setVisitorId("visitor-0002");
        open.setStatus(StoreCartStatus.ACTIVE);
        when(carts.findByCustomerIdAndStatus(44L, StoreCartStatus.ACTIVE)).thenReturn(List.of(open));

        service.recordOrder(null, null, 44L, 12L, BigDecimal.TEN);

        verify(events, never()).save(any());
        assertThat(open.getStatus()).isEqualTo(StoreCartStatus.ORDERED);
    }

    @Test
    void recordOrderWithoutASessionUsesTheVisitorId() {
        service.recordOrder("visitor-0001", "not valid!", 44L, 12L, BigDecimal.TEN);

        ArgumentCaptor<StoreEvent> captor = ArgumentCaptor.forClass(StoreEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("visitor-0001");
    }
}
