package com.warsha.erp.entities;

/**
 * What a store visitor did. Everything except {@link #ORDER_PLACED} is sent by the
 * store app to POST /events; ORDER_PLACED is written by the server at checkout so it
 * always matches a real order.
 */
public enum StoreEventType {
    PAGE_VIEW,
    PRODUCT_VIEW,
    VARIANT_SELECT,
    CART_ADD,
    CART_REMOVE,
    CART_UPDATE,
    CHECKOUT_START,
    SEARCH,
    VOUCHER_APPLY,
    ORDER_PLACED;

    public boolean sentByStore() {
        return this != ORDER_PLACED;
    }
}
