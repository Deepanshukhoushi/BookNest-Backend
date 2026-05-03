package com.booknest.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartRequest {
    private Long userId;
    private Long bookId;
    private Long itemId;
    private Integer quantity;
}
