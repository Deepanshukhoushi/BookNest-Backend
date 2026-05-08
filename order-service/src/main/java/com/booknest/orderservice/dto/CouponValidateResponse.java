package com.booknest.orderservice.dto;

import com.booknest.orderservice.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO returned when a user validates a coupon code during checkout.
 * Contains all the information the frontend needs to display the discount breakdown.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponValidateResponse {

    /** Whether the coupon is valid and can be applied */
    private boolean valid;

    /** The coupon code that was validated */
    private String code;

    /** How the discount is calculated */
    private DiscountType discountType;

    /** The raw discount value (percent or fixed amount) */
    private Double discountValue;

    /**
     * The actual monetary discount applied to the given subtotal.
     * This is what gets subtracted from the order total.
     */
    private Double discountAmount;

    /** The order subtotal after the discount is applied */
    private Double finalAmount;

    /** Human-readable message (success or reason for rejection) */
    private String message;
}
