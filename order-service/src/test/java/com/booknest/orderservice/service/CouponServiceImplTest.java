package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.CouponRequest;
import com.booknest.orderservice.dto.CouponValidateResponse;
import com.booknest.orderservice.entity.Coupon;
import com.booknest.orderservice.enums.DiscountType;
import com.booknest.orderservice.repository.CouponRepository;
import com.booknest.orderservice.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponServiceImpl couponService;

    private Coupon activePercentageCoupon;

    @BeforeEach
    void setUp() {
        activePercentageCoupon = Coupon.builder()
                .couponId(1L)
                .code("SAVE20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(20.0)
                .minOrderAmount(100.0)
                .maxUsage(10)
                .usageCount(1)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createCoupon_savesAndReturnsResponse() {
        CouponRequest request = CouponRequest.builder()
                .code("save20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(20.0)
                .minOrderAmount(100.0)
                .maxUsage(10)
                .expiryDate(LocalDateTime.now().plusDays(2))
                .build();

        when(couponRepository.existsByCodeIgnoreCase("SAVE20")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setCouponId(1L);
            return coupon;
        });

        var response = couponService.createCoupon(request);

        assertThat(response.getCouponId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("SAVE20");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void validateCoupon_invalidCode_throwsException() {
        when(couponRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.validateCoupon("NOPE", 500.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid coupon code");
    }

    @Test
    void validateCoupon_expiredCoupon_throwsException() {
        activePercentageCoupon.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(activePercentageCoupon));

        assertThatThrownBy(() -> couponService.validateCoupon("SAVE20", 500.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateCoupon_exhaustedCoupon_throwsException() {
        activePercentageCoupon.setMaxUsage(1);
        activePercentageCoupon.setUsageCount(1);
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(activePercentageCoupon));

        assertThatThrownBy(() -> couponService.validateCoupon("SAVE20", 500.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void validateCoupon_belowMinOrder_throwsException() {
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(activePercentageCoupon));

        assertThatThrownBy(() -> couponService.validateCoupon("SAVE20", 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum order amount");
    }

    @Test
    void validateCoupon_percentageDiscount_calculatesCorrectly() {
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(activePercentageCoupon));

        CouponValidateResponse response = couponService.validateCoupon("SAVE20", 500.0);

        assertThat(response.getDiscountAmount()).isEqualTo(100.0);
        assertThat(response.getFinalAmount()).isEqualTo(400.0);
    }

    @Test
    void validateCoupon_fixedDiscount_calculatesCorrectly() {
        Coupon fixedCoupon = Coupon.builder()
                .couponId(2L)
                .code("FLAT50")
                .discountType(DiscountType.FIXED)
                .discountValue(50.0)
                .minOrderAmount(100.0)
                .maxUsage(10)
                .usageCount(1)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(couponRepository.findByCodeIgnoreCase("FLAT50")).thenReturn(Optional.of(fixedCoupon));

        CouponValidateResponse response = couponService.validateCoupon("FLAT50", 500.0);

        assertThat(response.getDiscountAmount()).isEqualTo(50.0);
        assertThat(response.getFinalAmount()).isEqualTo(450.0);
    }

    @Test
    void validateCoupon_fixedDiscount_capsAtSubtotal() {
        Coupon fixedCoupon = Coupon.builder()
                .couponId(2L)
                .code("FLAT50")
                .discountType(DiscountType.FIXED)
                .discountValue(500.0)
                .minOrderAmount(0.0)
                .maxUsage(10)
                .usageCount(1)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(couponRepository.findByCodeIgnoreCase("FLAT50")).thenReturn(Optional.of(fixedCoupon));

        CouponValidateResponse response = couponService.validateCoupon("FLAT50", 120.0);

        assertThat(response.getDiscountAmount()).isEqualTo(120.0);
        assertThat(response.getFinalAmount()).isEqualTo(0.0);
    }

    @Test
    void applyCoupon_incrementsUsageCount() {
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(activePercentageCoupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.applyCoupon("SAVE20", 500.0);

        assertThat(activePercentageCoupon.getUsageCount()).isEqualTo(2);
        verify(couponRepository).save(activePercentageCoupon);
    }

    @Test
    void toggleActive_flipsCouponStatus() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(activePercentageCoupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = couponService.toggleActive(1L);

        assertThat(response.getActive()).isFalse();
    }

    @Test
    void getAllCoupons_returnsSortedResponses() {
        Coupon older = Coupon.builder()
                .couponId(2L)
                .code("OLDER")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(10.0)
                .minOrderAmount(0.0)
                .maxUsage(10)
                .usageCount(0)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(couponRepository.findAll()).thenReturn(List.of(older, activePercentageCoupon));

        var responses = couponService.getAllCoupons();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCode()).isEqualTo("SAVE20");
    }
}
