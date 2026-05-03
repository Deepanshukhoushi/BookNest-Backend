package com.booknest.walletservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for creating a new digital wallet.
 * Prevents mass assignment by explicitly defining which fields can be
 * provided during wallet initialization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletCreateRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @Min(value = 0, message = "Initial balance cannot be negative")
    private Double currentBalance;
}
