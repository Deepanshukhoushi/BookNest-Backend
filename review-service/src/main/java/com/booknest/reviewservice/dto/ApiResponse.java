package com.booknest.reviewservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local representation of the standard ApiResponse envelope used by all Booknest microservices.
 * Used to correctly deserialize Feign client responses that wrap data in this structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
