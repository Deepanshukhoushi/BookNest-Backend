package com.booknest.walletservice.controller;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.booknest.walletservice.dto.WalletRequest;
import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.Wallet;
import com.booknest.walletservice.payload.ApiResponse;
import com.booknest.walletservice.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/wallet")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WalletResource {

    private final WalletService walletService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Wallet>> addWallet(@Valid @RequestBody com.booknest.walletservice.dto.WalletCreateRequest request) {
        Wallet wallet = Wallet.builder()
                .userId(request.getUserId())
                .currentBalance(request.getCurrentBalance() != null ? request.getCurrentBalance() : 0.0)
                .build();
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet created successfully", walletService.addWallet(wallet)));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Wallet>>> getAllWallets() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallets fetched successfully", walletService.getWallets()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Wallet>> getById(@PathVariable Long id, HttpServletRequest request) {
        enforceWalletAccess(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet fetched successfully", walletService.getById(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Wallet>> getByUserId(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet fetched successfully", walletService.getByUserId(userId)));
    }

    @PostMapping("/addMoney")
    public ResponseEntity<ApiResponse<Wallet>> addMoney(@Valid @RequestBody WalletRequest request, HttpServletRequest httpRequest) {
        enforceWalletAccess(request.getWalletId(), httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Money added successfully", walletService.addMoney(request.getWalletId(), request.getAmount(), request.getPaymentGateway())));
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<Wallet>> payMoney(@Valid @RequestBody WalletRequest request, HttpServletRequest httpRequest) {
        enforceWalletAccess(request.getWalletId(), httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment successful", walletService.payMoney(request.getWalletId(), request.getAmount(), request.getOrderId())));
    }

    @GetMapping("/statements/{walletId}")
    public ResponseEntity<ApiResponse<List<Statement>>> getStatements(@PathVariable Long walletId, HttpServletRequest request) {
        enforceWalletAccess(walletId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet statements fetched successfully", walletService.getStatementsByWalletId(walletId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteById(@PathVariable Long id, HttpServletRequest request) {
        enforceWalletAccess(id, request);
        walletService.deleteById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet deleted successfully", null));
    }

    @PostMapping("/initialize/{userId}")
    public ResponseEntity<ApiResponse<Wallet>> initializeWallet(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wallet initialized successfully", walletService.initializeWallet(userId)));
    }

    @PostMapping("/initiate-razorpay")
    public ResponseEntity<ApiResponse<String>> initiateRazorpayTopUp(@Valid @RequestBody WalletRequest request, HttpServletRequest httpRequest) {
        enforceWalletAccess(request.getWalletId(), httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(true, "Razorpay payment initiated", walletService.initiateRazorpayTopUp(request)));
    }

    @PostMapping("/verify-razorpay")
    public ResponseEntity<ApiResponse<Wallet>> verifyRazorpayTopUp(@RequestBody PaymentVerifyRequest request, HttpServletRequest httpRequest) {
        Long authenticatedUserId = resolveUserId(httpRequest);
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User authentication required");
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment verified and wallet updated", walletService.verifyRazorpayTopUp(request, authenticatedUserId)));
    }

    @GetMapping("/razorpay-key")
    public ResponseEntity<ApiResponse<String>> getRazorpayPublicKey() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Razorpay public key fetched", walletService.getRazorpayPublicKey()));
    }

    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        if (targetUserId == null || request == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            return;
        }

        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private void enforceWalletAccess(Long walletId, HttpServletRequest request) {
        if (walletId == null || request == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Wallet wallet = walletService.getById(walletId);
        enforceUserAccess(wallet.getUserId(), request);
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
