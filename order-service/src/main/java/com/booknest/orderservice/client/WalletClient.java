package com.booknest.orderservice.client;

import com.booknest.orderservice.dto.WalletDTO;
import com.booknest.orderservice.dto.WalletRequest;
import com.booknest.orderservice.payload.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "WALLET-SERVICE")
public interface WalletClient {

    @GetMapping("/api/v1/wallet/user/{userId}")
    ApiResponse<WalletDTO> getWalletByUserId(@PathVariable("userId") Long userId);

    @PostMapping("/api/v1/wallet/pay")
    ApiResponse<WalletDTO> payMoney(@RequestBody WalletRequest request);

    @PostMapping("/api/v1/wallet/addMoney")
    ApiResponse<WalletDTO> addMoney(@RequestBody WalletRequest request);
}
