package com.warsha.erp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warsha.erp.dtos.TrackEventsRequest;
import com.warsha.erp.entities.*;
import com.warsha.erp.exceptions.ValidationException;
import com.warsha.erp.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Records what store visitors do (POST /events) and keeps each visitor's latest cart,
 * so the ERP can report funnels, product interest and abandoned carts.
 * <p>
 * Tracking must never get in the way of shopping: bots and over-limit requests are
 * dropped silently.
 */
@Slf4j
@Service
public class StoreTrackingService {

    /** Browser clocks drift; events claiming to be older than this, or in the future, get "now". */
    static final Duration MAX_CLOCK_SKEW = Duration.ofDays(1);

    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|headless|lighthouse|pingdom|monitor", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DEVICE_TYPES = Set.of("MOBILE", "TABLET", "DESKTOP");

    private final StoreEventRepository events;
    private final StoreVisitorRepository visitors;
    private final StoreCartRepository carts;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final TrackingRateLimiter rateLimiter;
    private final ObjectMapper json;
    private Clock clock = Clock.systemDefaultZone();

    public StoreTrackingService(StoreEventRepository events, StoreVisitorRepository visitors,
                                StoreCartRepository carts, ProductRepository products,
                                ProductVariantRepository variants, TrackingRateLimiter rateLimiter,
                                ObjectMapper json) {
        this.events = events;
        this.visitors = visitors;
        this.carts = carts;
        this.products = products;
        this.variants = variants;
        this.rateLimiter = rateLimiter;
        this.json = json;
    }

    /** Tests only. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------- POST /events

    /**
     * Stores one batch. {@code customerId} is the logged-in customer, or null for guests;
     * the first time a visitor is seen logged in, their earlier guest events become theirs.
     */
    @Transactional
    public void track(TrackEventsRequest request, Long customerId, String userAgent, String clientIp) {
        if (userAgent != null && BOT.matcher(userAgent).find()) {
            return;
        }
        if (!rateLimiter.allow(request.getVisitorId(), clientIp)) {
            log.debug("Dropped tracking batch from visitor {} (rate limit)", request.getVisitorId());
            return;
        }
        for (TrackEventsRequest.Event e : request.getEvents()) {
            if (!e.getType().sentByStore()) {
                throw new ValidationException("events", e.getType() + " is recorded by the server, not sent by the store.");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String device = device(request.getDeviceType());
        touchVisitor(request, customerId, device, now);

        List<StoreEvent> rows = request.getEvents().stream()
                .map(e -> toEvent(e, request, customerId, device, now))
                .toList();
        events.saveAll(rows);

        if (request.getCart() != null) {
            saveCart(request.getVisitorId(), customerId, request.getCart().getItems(), now);
        }
    }

    private void touchVisitor(TrackEventsRequest request, Long customerId, String device, LocalDateTime now) {
        StoreVisitor visitor = visitors.findById(request.getVisitorId()).orElseGet(() -> {
            StoreVisitor v = new StoreVisitor();
            v.setId(request.getVisitorId());
            v.setFirstSeenAt(now);
            v.setFirstLandingPage(clean(request.getLandingPage(), 200));
            v.setFirstReferrer(clean(request.getReferrer(), 300));
            v.setFirstUtmSource(clean(request.getUtmSource(), 100));
            v.setFirstUtmMedium(clean(request.getUtmMedium(), 100));
            v.setFirstUtmCampaign(clean(request.getUtmCampaign(), 100));
            return v;
        });
        visitor.setLastSeenAt(now);
        if (device != null) {
            visitor.setDeviceType(device);
        }
        if (customerId != null && !customerId.equals(visitor.getCustomerId())) {
            visitor.setCustomerId(customerId);
            visitors.save(visitor);
            events.assignCustomer(visitor.getId(), customerId);
            return;
        }
        visitors.save(visitor);
    }

    private StoreEvent toEvent(TrackEventsRequest.Event e, TrackEventsRequest request, Long customerId,
                               String device, LocalDateTime now) {
        StoreEvent row = new StoreEvent();
        row.setType(e.getType());
        row.setVisitorId(request.getVisitorId());
        row.setSessionId(request.getSessionId());
        row.setCustomerId(customerId);
        row.setProductId(e.getProductId());
        row.setVariantId(e.getVariantId());
        row.setQuantity(e.getQuantity());
        row.setSearchQuery(clean(e.getSearchQuery(), 100));
        row.setResultCount(e.getResultCount());
        row.setVoucherCode(VoucherService.normalize(clean(e.getVoucherCode(), 50)));
        row.setVoucherResult(clean(e.getVoucherResult(), 30));
        row.setPagePath(clean(e.getPath(), 200));
        row.setReferrer(clean(request.getReferrer(), 300));
        row.setUtmSource(clean(request.getUtmSource(), 100));
        row.setUtmMedium(clean(request.getUtmMedium(), 100));
        row.setUtmCampaign(clean(request.getUtmCampaign(), 100));
        row.setDeviceType(device);
        row.setOccurredAt(occurredAt(e, now));
        row.setReceivedAt(now);
        return row;
    }

    private LocalDateTime occurredAt(TrackEventsRequest.Event e, LocalDateTime now) {
        if (e.getOccurredAt() == null) {
            return now;
        }
        LocalDateTime at = LocalDateTime.ofInstant(e.getOccurredAt(), clock.getZone());
        boolean plausible = !at.isAfter(now) && !at.isBefore(now.minus(MAX_CLOCK_SKEW));
        return plausible ? at : now;
    }

    // ------------------------------------------------------------------- carts

    private void saveCart(String visitorId, Long customerId, List<TrackEventsRequest.CartLine> lines, LocalDateTime now) {
        StoreCart cart = carts.findById(visitorId).orElseGet(() -> {
            StoreCart c = new StoreCart();
            c.setVisitorId(visitorId);
            return c;
        });
        if (cart.getStatus() == StoreCartStatus.ORDERED && lines.isEmpty()) {
            return; // the store empties its cart after checkout; keep the ordered record
        }
        if (customerId != null) {
            cart.setCustomerId(customerId);
        }
        cart.setItems(toJson(lines));
        cart.setItemCount(lines.stream().mapToInt(TrackEventsRequest.CartLine::getQuantity).sum());
        cart.setCartValue(price(lines));
        cart.setStatus(lines.isEmpty() ? StoreCartStatus.EMPTY : StoreCartStatus.ACTIVE);
        cart.setOrderId(null);
        cart.setOrderedAt(null);
        cart.setUpdatedAt(now);
        carts.save(cart);
    }

    /** Σ (variant or product price − product discount) × quantity, at today's prices. */
    BigDecimal price(List<TrackEventsRequest.CartLine> lines) {
        if (lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, Product> productById = products.findAllById(
                        lines.stream().map(TrackEventsRequest.CartLine::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getProductID, Function.identity()));
        Map<Long, ProductVariant> variantById = variants.findAllById(
                        lines.stream().map(TrackEventsRequest.CartLine::getVariantId).filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        for (TrackEventsRequest.CartLine line : lines) {
            Product product = productById.get(line.getProductId());
            if (product == null) {
                continue; // deleted since it went into the cart
            }
            ProductVariant variant = line.getVariantId() != null ? variantById.get(line.getVariantId()) : null;
            double base = variant != null && variant.getSellingPrice() != null
                    ? variant.getSellingPrice() : orZero(product.getSellingPrice());
            double unit = Math.max(0, base - orZero(product.getDiscount()));
            total = total.add(BigDecimal.valueOf(unit).multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String toJson(List<TrackEventsRequest.CartLine> lines) {
        List<Map<String, Object>> plain = lines.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", l.getProductId());
            m.put("variantId", l.getVariantId());
            m.put("quantity", l.getQuantity());
            return m;
        }).toList();
        try {
            return json.writeValueAsString(plain);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write cart JSON", e);
        }
    }

    // ---------------------------------------------------------------- checkout

    /**
     * Called after a store order is saved: logs ORDER_PLACED and marks the visitor's cart,
     * and any other open cart of the same customer, as ordered. {@code visitorId} and
     * {@code sessionId} come from headers that older store builds don't send, so either
     * may be null. The caller must not let a failure here fail the checkout.
     */
    @Transactional
    public void recordOrder(String visitorId, String sessionId, Long customerId, Long orderId, BigDecimal orderValue) {
        LocalDateTime now = LocalDateTime.now(clock);
        String visitor = validId(visitorId);
        if (visitor != null) {
            StoreEvent row = new StoreEvent();
            row.setType(StoreEventType.ORDER_PLACED);
            row.setVisitorId(visitor);
            row.setSessionId(Objects.requireNonNullElse(validId(sessionId), visitor));
            row.setCustomerId(customerId);
            row.setOrderId(orderId);
            row.setOrderValue(orderValue);
            row.setOccurredAt(now);
            row.setReceivedAt(now);
            events.save(row);
        }

        Map<String, StoreCart> open = new LinkedHashMap<>();
        if (visitor != null) {
            carts.findById(visitor).ifPresent(c -> open.put(c.getVisitorId(), c));
        }
        if (customerId != null) {
            carts.findByCustomerIdAndStatus(customerId, StoreCartStatus.ACTIVE)
                    .forEach(c -> open.putIfAbsent(c.getVisitorId(), c));
        }
        for (StoreCart cart : open.values()) {
            cart.setStatus(StoreCartStatus.ORDERED);
            cart.setOrderId(orderId);
            cart.setOrderedAt(now);
            if (customerId != null) {
                cart.setCustomerId(customerId);
            }
        }
        carts.saveAll(open.values());
    }

    // ----------------------------------------------------------------- helpers

    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String device(String value) {
        String upper = value != null ? value.trim().toUpperCase(Locale.ROOT) : null;
        return DEVICE_TYPES.contains(upper) ? upper : null;
    }

    private static String validId(String value) {
        return value != null && value.matches(TrackEventsRequest.ID_PATTERN) ? value : null;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0;
    }
}
