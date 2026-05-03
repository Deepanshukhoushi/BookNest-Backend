package com.booknest.wishlistservice.client;

import com.booknest.wishlistservice.dto.CartRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "CART-SERVICE")
public interface CartClient {

    @PostMapping("/api/v1/cart/add")
    void addToCart(@RequestBody CartRequest request);
}
