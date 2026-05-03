package com.booknest.orderservice.controller;

import com.booknest.orderservice.dto.PaymentVerifyRequest;
import com.booknest.orderservice.dto.PaymentInitiationRequest;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.payload.ApiResponse;
import com.booknest.orderservice.service.OrderService;
import com.booknest.orderservice.service.RazorpayService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class PaymentResource {

    private final OrderService orderService;
    private final RazorpayService razorpayService;

    @PostMapping("/initiate/{userId}")
    public ResponseEntity<ApiResponse<String>> initiatePayment(
            @PathVariable Long userId,
            @RequestBody(required = false) PaymentInitiationRequest initiationRequest,
            HttpServletRequest request) {
        enforceUserAccess(userId, request);
        log.info("REST request to initiate payment for userId: {}", userId);
        Long addressId = initiationRequest == null ? null : initiationRequest.getAddressId();
        String razorpayOrderId = orderService.initiateRazorpayPayment(userId, addressId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment initiated", razorpayOrderId));
    }

    @GetMapping("/config/public-key")
    public ResponseEntity<ApiResponse<String>> getRazorpayPublicKey() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Public key fetched", razorpayService.getPublicKey()));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<List<Order>>> verifyPayment(
            @RequestBody PaymentVerifyRequest request,
            HttpServletRequest httpRequest) {
        log.info("REST request to verify payment: {}", request.getOrderId());
        Long authenticatedUserId = resolveUserId(httpRequest);
        List<Order> orders = orderService.verifyRazorpayPayment(request, authenticatedUserId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment verified successfully", orders));
    }

    @PostMapping("/webhook")
    @SecurityRequirement(name = "none") // Webhooks are called by Razorpay, not authorized via Bearer
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        log.info("Received Razorpay Webhook");
        orderService.handlePaymentWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user context is required");
        }

        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private String resolveRole(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedRole");
        if (attribute instanceof String role && !role.isBlank()) {
            return role;
        }
        return request.getHeader("X-Auth-Role");
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedUserId");
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Integer userId) {
            return userId.longValue();
        }

        String userIdHeader = request.getHeader("X-Auth-UserId");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
        }
    }

    private boolean isAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        String normalized = roleHeader.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized);
    }
}
