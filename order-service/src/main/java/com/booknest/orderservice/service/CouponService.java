package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.CouponRequest;
import com.booknest.orderservice.dto.CouponResponse;
import com.booknest.orderservice.dto.CouponValidateResponse;

import java.util.List;

public interface CouponService {

    CouponResponse createCoupon(CouponRequest request);

    List<CouponResponse> getAllCoupons();

    void deleteCoupon(Long couponId);

    CouponResponse toggleActive(Long couponId);

    CouponValidateResponse validateCoupon(String code, double subtotal);

    CouponValidateResponse applyCoupon(String code, double subtotal);
}
