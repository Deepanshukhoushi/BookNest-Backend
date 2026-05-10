package com.booknest.walletservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private Long orderId;
    private Long userId;
    private String type; // ORDER, PAYMENT, DELIVERY
    private String message;
    private String status;
    private Double amount;
    private LocalDateTime timestamp;
}
