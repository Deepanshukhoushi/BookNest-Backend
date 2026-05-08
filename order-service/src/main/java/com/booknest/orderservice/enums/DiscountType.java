package com.booknest.orderservice.enums;

/**
 * Defines how a coupon discount is calculated.
 * PERCENTAGE: deducts a percentage of the order subtotal (e.g. 20% off).
 * FIXED:       deducts a flat monetary amount (e.g. ₹50 off), capped at the subtotal.
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED
}
