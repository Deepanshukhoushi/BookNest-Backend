package com.booknest.walletservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for wallet operations: top-up, debit, and order payment.
 * Validates that a user identity and a positive amount are always provided.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    // Optional — required only for statement / direct wallet operations
    private Long walletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private Double amount;

    // Optional — the order being paid for
    private Long orderId;

    // The gateway used for top-ups (e.g., 'card', 'upi')
    private String paymentGateway;
}
