package com.booknest.walletservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for verifying a Razorpay payment.
 * Contains the unique order, payment, and signature details provided by Razorpay.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerifyRequest {
    private String orderId;
    private String paymentId;
    private String signature;
    private Double amount;
}
