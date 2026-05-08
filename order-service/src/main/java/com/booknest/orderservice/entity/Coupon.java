package com.booknest.orderservice.entity;

import com.booknest.orderservice.enums.DiscountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a promotional coupon code that can be applied during checkout.
 * Supports both percentage-based and fixed-amount discounts with optional
 * expiry dates, usage limits, and minimum order amount requirements.
 */
@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long couponId;

    /** Unique, case-insensitive coupon code entered by the user (e.g. "SAVE20") */
    @Column(unique = true, nullable = false, length = 50)
    @NotBlank
    private String code;

    /** How the discount is computed — percentage of subtotal or flat amount */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private DiscountType discountType;

    /**
     * The discount magnitude.
     * For PERCENTAGE: 0.0–100.0 (represents %).
     * For FIXED: monetary amount in INR.
     */
    @Column(nullable = false)
    @NotNull
    @DecimalMin("0.01")
    private Double discountValue;

    /** Minimum cart subtotal (before discount) required to apply this coupon. 0 = no minimum. */
    @Builder.Default
    @Column(nullable = false)
    private Double minOrderAmount = 0.0;

    /** Maximum number of times this coupon can be used across all users. null = unlimited. */
    @Column
    private Integer maxUsage;

    /** How many times this coupon has been successfully applied at checkout. */
    @Builder.Default
    @Column(nullable = false)
    private Integer usageCount = 0;

    /** When this coupon expires. null = never expires. */
    @Column
    private LocalDateTime expiryDate;

    /** Whether this coupon is currently enabled for use. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** Timestamp when this coupon was created by the admin. */
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
