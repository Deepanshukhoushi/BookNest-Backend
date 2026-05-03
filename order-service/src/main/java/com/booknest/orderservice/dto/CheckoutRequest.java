package com.booknest.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for the main checkout operation.
 * Validates that a user identity and a valid payment method are always provided.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    // Optional — if null the service uses the most-recently saved address
    private Long addressId;

    @NotBlank(message = "Payment method is required")
    @Pattern(
        regexp = "^(COD|WALLET|ONLINE)$",
        message = "Payment method must be one of: COD, WALLET, ONLINE"
    )
    private String paymentMethod;

    // Optional payment provider metadata (e.g., Razorpay order details)
    private Map<String, Object> paymentDetails;

    // Optional discount / promo code
    private String discountCode;
}
