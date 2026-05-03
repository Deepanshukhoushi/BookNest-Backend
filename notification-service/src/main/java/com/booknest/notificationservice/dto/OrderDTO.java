package com.booknest.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {
    private Long orderId;
    private Long userId;
    private LocalDateTime orderDate;
    private Double amountPaid;
    private String paymentMethod;
    private String orderStatus;
    private Integer quantity;
    private Long bookId;
    private String bookName;
    private AddressDTO address;
}
