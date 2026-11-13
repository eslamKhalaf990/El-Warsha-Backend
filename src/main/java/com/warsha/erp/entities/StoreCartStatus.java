package com.warsha.erp.entities;

/**
 * State of a visitor's cart. "Abandoned" is not stored: it is an ACTIVE cart that has
 * not changed for a while, so the cut-off can change without rewriting rows.
 */
public enum StoreCartStatus {
    ACTIVE,
    EMPTY,
    ORDERED
}
