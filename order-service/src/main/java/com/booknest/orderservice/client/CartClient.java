package com.booknest.orderservice.client;

import com.booknest.orderservice.dto.CartDTO;
import com.booknest.orderservice.payload.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "CART-SERVICE")
public interface CartClient {

    @GetMapping("/api/v1/cart/{userId}")
    ApiResponse<CartDTO> getCartByUserId(@PathVariable("userId") Long userId);

    @DeleteMapping("/api/v1/cart/clear/{userId}")
    ApiResponse<Object> clearCartByUserId(@PathVariable("userId") Long userId);
}
