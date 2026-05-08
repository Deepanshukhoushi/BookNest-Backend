package com.booknest.orderservice.controller;

import com.booknest.orderservice.dto.CouponRequest;
import com.booknest.orderservice.dto.CouponResponse;
import com.booknest.orderservice.dto.CouponValidateResponse;
import com.booknest.orderservice.dto.CouponValidationRequest;
import com.booknest.orderservice.payload.ApiResponse;
import com.booknest.orderservice.service.CouponService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CouponResource {

    private final CouponService couponService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getAllCoupons() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupons fetched successfully", couponService.getAllCoupons()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon created successfully", couponService.createCoupon(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon deleted successfully", null));
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponResponse>> toggleCoupon(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Coupon status updated successfully", couponService.toggleActive(id)));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CouponValidateResponse>> validateCoupon(@Valid @RequestBody CouponValidationRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Coupon validated successfully",
                couponService.validateCoupon(request.getCode(), request.getSubtotal())
        ));
    }
}
