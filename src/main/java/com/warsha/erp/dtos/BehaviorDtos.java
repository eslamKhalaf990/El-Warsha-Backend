package com.warsha.erp.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Responses of {@code /analytics/behavior}: what store visitors did, from the tracked
 * events. Rates are fractions (0.25 = 25%) and 0 when there is nothing to divide by.
 * Money is in EGP.
 */
public final class BehaviorDtos {

    private BehaviorDtos() {
    }

    public record Range(LocalDate from, LocalDate to, long days) {
    }

    /** Headline counts, the session funnel, devices and one row per day. */
    public record Overview(
            Range range,
            long visitors,
            long newVisitors,
            long sessions,
            long pageViews,
            long productViews,
            long cartAdds,
            long cartRemoves,
            long checkoutsStarted,
            long orders,
            double orderValue,
            Funnel funnel,
            List<DeviceRow> devices,
            List<DayRow> days
    ) {
    }

    /**
     * Sessions that reached each step (each step counted on its own), and the share
     * of all sessions that ended in an order.
     */
    public record Funnel(
            long sessions,
            long viewedProduct,
            long addedToCart,
            long startedCheckout,
            long ordered,
            double viewToCartRate,
            double cartToCheckoutRate,
            double checkoutToOrderRate,
            double conversionRate
    ) {
    }

    public record DeviceRow(String device, long sessions, double share) {
    }

    public record DayRow(LocalDate date, long visitors, long sessions, long productViews, long cartAdds, long orders) {
    }

    /** Interest in one product: views and cart activity from events, units from tracked orders. */
    public record ProductRow(
            long productId,
            String name,
            long views,
            long viewers,
            long cartAdds,
            long cartRemoves,
            long orders,
            long unitsOrdered,
            double viewToCartRate,
            double cartToOrderRate
    ) {
    }

    /**
     * Carts with items, no order, and no change for at least {@code idleMinutes}.
     * {@code orderedCarts} counts carts that did turn into an order in the range.
     */
    public record AbandonedCarts(
            Range range,
            int idleMinutes,
            long count,
            double value,
            long orderedCarts,
            double abandonmentRate,
            List<AbandonedCart> carts
    ) {
    }

    public record AbandonedCart(
            String visitorId,
            Long customerId,
            String customerName,
            String phone,
            String email,
            int itemCount,
            double value,
            LocalDateTime lastActivity,
            String source,
            String device,
            List<CartItem> items
    ) {
    }

    public record CartItem(long productId, Long variantId, String name, String variant, int quantity) {
    }

    /** {@code averageResults} is null when the store never reported a count for the query. */
    public record SearchRow(String query, long searches, long visitors, Double averageResults, long noResultSearches) {
    }

    public record Searches(long total, long noResults, List<SearchRow> top, List<SearchRow> withoutResults) {
    }

    /** {@code rejections} maps each rejection reason to how often it happened. */
    public record VoucherRow(String code, long tries, long visitors, long applied, long rejected,
                             Map<String, Long> rejections) {
    }

    /** Where visitors first came from, and how many of them ordered in the range. */
    public record SourceRow(String source, long visitors, long orderingVisitors, long orders, double orderValue,
                            double conversionRate) {
    }
}
