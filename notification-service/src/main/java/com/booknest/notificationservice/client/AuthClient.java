package com.booknest.notificationservice.client;

import com.booknest.notificationservice.dto.ApiResponse;
import com.booknest.notificationservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "auth-service", configuration = com.booknest.notificationservice.config.InternalFeignConfig.class)
public interface AuthClient {
    @GetMapping("/api/v1/auth/profile/{userId}")
    ApiResponse<UserDTO> getUserById(@PathVariable("userId") Long userId);

    @GetMapping("/api/v1/auth/all")
    ApiResponse<List<UserDTO>> getAllUsers();
}
