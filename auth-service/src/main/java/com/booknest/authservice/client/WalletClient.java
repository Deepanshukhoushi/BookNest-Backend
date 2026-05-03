package com.booknest.authservice.client;

import com.booknest.authservice.dto.ApiResponse;
import com.booknest.authservice.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "wallet-service", configuration = InternalFeignConfig.class)
public interface WalletClient {
    @PostMapping("/api/v1/wallet/initialize/{userId}")
    ApiResponse<Object> initializeWallet(@PathVariable("userId") Long userId);
}
