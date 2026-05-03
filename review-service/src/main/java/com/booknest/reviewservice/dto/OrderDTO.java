package com.booknest.reviewservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {
    private Long orderId;
    private Long userId;
    private Long bookId;
    private String bookName;
    private String orderStatus;
}
