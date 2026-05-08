package com.booknest.orderservice.dto;

import com.booknest.orderservice.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for admin-created coupon requests.
 * All fields are validated at the controller layer before processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRequest {

    @NotBlank(message = "Coupon code is required")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must contain only uppercase letters, digits, hyphens, or underscores")
    private String code;

    @NotNull(message = "Discount type is required (PERCENTAGE or FIXED)")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
    @DecimalMax(value = "100.0", message = "Percentage discount cannot exceed 100%")
    private Double discountValue;

    /** Minimum subtotal required to use this coupon. Defaults to 0 (no minimum). */
    @DecimalMin(value = "0.0", message = "Minimum order amount cannot be negative")
    @Builder.Default
    private Double minOrderAmount = 0.0;

    /** Maximum total usages across all users. null = unlimited. */
    @Min(value = 1, message = "Max usage must be at least 1 if provided")
    private Integer maxUsage;

    /** Optional expiry date-time. null = never expires. */
    private LocalDateTime expiryDate;
}
