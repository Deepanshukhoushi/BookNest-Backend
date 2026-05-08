package com.booknest.orderservice.dto;

import com.booknest.orderservice.enums.DiscountType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO returned by the admin coupon listing and create endpoints.
 * Includes computed boolean flags for frontend display logic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResponse {

    private Long couponId;
    private String code;
    private DiscountType discountType;
    private Double discountValue;
    private Double minOrderAmount;
    private Integer maxUsage;
    private Integer usageCount;
    private LocalDateTime expiryDate;
    private Boolean active;
    private LocalDateTime createdAt;

    /** true when expiryDate is set and is in the past */
    @JsonProperty("isExpired")
    private boolean isExpired;

    /** true when maxUsage is set and usageCount >= maxUsage */
    @JsonProperty("isExhausted")
    private boolean isExhausted;
}
