package com.warsha.erp.dtos;

import com.warsha.erp.entities.StoreEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Body of POST /events: a batch of store visitor actions. The store app sends one batch
 * every few seconds, plus its whole cart whenever the cart changed. Long text is cut
 * rather than rejected, so one odd page title never loses a whole batch.
 */
@Getter
@Setter
public class TrackEventsRequest {

    /** Ids are UUIDs made by the store app; anything that looks like one is accepted. */
    public static final String ID_PATTERN = "^[A-Za-z0-9-]{8,36}$";
    public static final int MAX_EVENTS = 50;
    public static final int MAX_CART_LINES = 100;

    @NotBlank(message = "visitorId is required.")
    @Pattern(regexp = ID_PATTERN, message = "visitorId is not a valid id.")
    private String visitorId;

    @NotBlank(message = "sessionId is required.")
    @Pattern(regexp = ID_PATTERN, message = "sessionId is not a valid id.")
    private String sessionId;

    /** MOBILE, TABLET or DESKTOP; anything else is stored as unknown. */
    private String deviceType;

    /** Where the session came from, captured on its first page. */
    private String landingPage;
    private String referrer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;

    @NotNull(message = "events is required.")
    @Size(max = MAX_EVENTS, message = "Send at most " + MAX_EVENTS + " events at a time.")
    private List<@Valid @NotNull Event> events;

    /** The visitor's whole cart; null when it did not change since the last batch. */
    @Valid
    private Cart cart;

    @Getter
    @Setter
    public static class Event {

        @NotNull(message = "Every event needs a type.")
        private StoreEventType type;

        /** When it happened in the browser; the server clamps it to the last day. */
        private Instant occurredAt;

        private String path;

        @Positive(message = "productId must be a positive number.")
        private Long productId;

        @Positive(message = "variantId must be a positive number.")
        private Long variantId;

        @Min(value = 0, message = "quantity can't be negative.")
        @Max(value = 999, message = "quantity can be at most 999.")
        private Integer quantity;

        /** SEARCH: what was typed and how many products matched. */
        private String searchQuery;

        @PositiveOrZero(message = "resultCount can't be negative.")
        private Integer resultCount;

        /** VOUCHER_APPLY: the code tried and APPLIED or the rejection reason. */
        private String voucherCode;
        private String voucherResult;
    }

    @Getter
    @Setter
    public static class Cart {

        @NotNull(message = "cart.items is required.")
        @Size(max = MAX_CART_LINES, message = "A cart can have at most " + MAX_CART_LINES + " lines.")
        private List<@Valid @NotNull CartLine> items;
    }

    @Getter
    @Setter
    public static class CartLine {

        @NotNull(message = "Every cart line needs a productId.")
        @Positive(message = "productId must be a positive number.")
        private Long productId;

        @Positive(message = "variantId must be a positive number.")
        private Long variantId;

        @NotNull(message = "Every cart line needs a quantity.")
        @Min(value = 1, message = "quantity must be at least 1.")
        @Max(value = 999, message = "quantity can be at most 999.")
        private Integer quantity;
    }
}
