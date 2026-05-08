package com.booknest.orderservice.service.impl;

import com.booknest.orderservice.dto.CouponRequest;
import com.booknest.orderservice.dto.CouponResponse;
import com.booknest.orderservice.dto.CouponValidateResponse;
import com.booknest.orderservice.entity.Coupon;
import com.booknest.orderservice.enums.DiscountType;
import com.booknest.orderservice.repository.CouponRepository;
import com.booknest.orderservice.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        String normalizedCode = normalizeCode(request.getCode());
        if (couponRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Coupon code already exists");
        }

        validateDiscountRequest(request);

        Coupon coupon = Coupon.builder()
                .code(normalizedCode)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount() == null ? 0.0 : request.getMinOrderAmount())
                .maxUsage(request.getMaxUsage())
                .usageCount(0)
                .expiryDate(request.getExpiryDate())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> getAllCoupons() {
        return couponRepository.findAll().stream()
                .sorted(Comparator.comparing(Coupon::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Coupon not found"));
        couponRepository.delete(coupon);
    }

    @Override
    @Transactional
    public CouponResponse toggleActive(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Coupon not found"));
        coupon.setActive(!Boolean.TRUE.equals(coupon.getActive()));
        return toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidateResponse validateCoupon(String code, double subtotal) {
        Coupon coupon = loadValidCoupon(code, subtotal);
        return buildValidationResponse(coupon, subtotal, "Coupon applied successfully");
    }

    @Override
    @Transactional
    public CouponValidateResponse applyCoupon(String code, double subtotal) {
        Coupon coupon = loadValidCoupon(code, subtotal);
        coupon.setUsageCount((coupon.getUsageCount() == null ? 0 : coupon.getUsageCount()) + 1);
        couponRepository.save(coupon);
        return buildValidationResponse(coupon, subtotal, "Coupon applied successfully");
    }

    private Coupon loadValidCoupon(String code, double subtotal) {
        if (subtotal < 0) {
            throw new IllegalArgumentException("Subtotal cannot be negative");
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Invalid coupon code"));

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new IllegalArgumentException("Coupon is inactive");
        }

        if (isExpired(coupon)) {
            throw new IllegalArgumentException("Coupon has expired");
        }

        if (isExhausted(coupon)) {
            throw new IllegalArgumentException("Coupon usage limit has been reached");
        }

        double minOrderAmount = coupon.getMinOrderAmount() == null ? 0.0 : coupon.getMinOrderAmount();
        if (subtotal < minOrderAmount) {
            throw new IllegalArgumentException("Coupon requires a minimum order amount of " + minOrderAmount);
        }

        return coupon;
    }

    private CouponValidateResponse buildValidationResponse(Coupon coupon, double subtotal, String message) {
        double discountAmount = calculateDiscountAmount(coupon, subtotal);
        double finalAmount = roundCurrency(subtotal - discountAmount);

        return CouponValidateResponse.builder()
                .valid(true)
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .message(message)
                .build();
    }

    private double calculateDiscountAmount(Coupon coupon, double subtotal) {
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            return roundCurrency(subtotal * (coupon.getDiscountValue() / 100.0));
        }
        return roundCurrency(Math.min(coupon.getDiscountValue(), subtotal));
    }

    private CouponResponse toResponse(Coupon coupon) {
        return CouponResponse.builder()
                .couponId(coupon.getCouponId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .maxUsage(coupon.getMaxUsage())
                .usageCount(coupon.getUsageCount())
                .expiryDate(coupon.getExpiryDate())
                .active(coupon.getActive())
                .createdAt(coupon.getCreatedAt())
                .isExpired(isExpired(coupon))
                .isExhausted(isExhausted(coupon))
                .build();
    }

    private boolean isExpired(Coupon coupon) {
        return coupon.getExpiryDate() != null && !coupon.getExpiryDate().isAfter(LocalDateTime.now());
    }

    private boolean isExhausted(Coupon coupon) {
        return coupon.getMaxUsage() != null
                && (coupon.getUsageCount() == null ? 0 : coupon.getUsageCount()) >= coupon.getMaxUsage();
    }

    private void validateDiscountRequest(CouponRequest request) {
        if (request.getDiscountType() == DiscountType.PERCENTAGE && request.getDiscountValue() > 100.0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
        if (request.getExpiryDate() != null && !request.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Expiry date must be in the future");
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
