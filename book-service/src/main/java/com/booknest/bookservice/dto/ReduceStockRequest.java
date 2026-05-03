package com.booknest.bookservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for decreasing the inventory of a specific book.
 * Typically used when an order is finalized to update available stock.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReduceStockRequest {
    private Long bookId;
    private Integer quantity;
}

