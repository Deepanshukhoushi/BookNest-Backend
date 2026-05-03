package com.booknest.wishlistservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartRequest {
    private Long userId;
    private Long bookId;
    private Long itemId;
    private Integer quantity;
}
