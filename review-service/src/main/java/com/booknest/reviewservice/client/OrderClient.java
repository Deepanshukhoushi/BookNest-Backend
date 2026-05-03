package com.booknest.reviewservice.client;

import com.booknest.reviewservice.dto.ApiResponse;
import com.booknest.reviewservice.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client for inter-service communication with ORDER-SERVICE.
 * The return type correctly reflects the ApiResponse<T> wrapper that order-service uses
 * for all its endpoints, preventing Feign deserialization failures.
 */
@FeignClient(name = "ORDER-SERVICE")
public interface OrderClient {

    @GetMapping("/api/v1/orders/user/{userId}")
    ApiResponse<List<OrderDTO>> getOrdersByUserId(@PathVariable("userId") Long userId);
}
