package com.booknest.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerifyRequest {
    private String orderId;
    private String paymentId;
    private String signature;
    private Long addressId;
    private String discountCode;
}
