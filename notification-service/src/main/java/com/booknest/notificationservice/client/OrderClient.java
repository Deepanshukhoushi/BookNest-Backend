package com.booknest.notificationservice.client;

import com.booknest.notificationservice.config.InternalFeignConfig;
import com.booknest.notificationservice.dto.ApiResponse;
import com.booknest.notificationservice.dto.OrderDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/api/v1/orders", configuration = InternalFeignConfig.class)
public interface OrderClient {

    @GetMapping("/{orderId}")
    ApiResponse<OrderDTO> getOrderById(@PathVariable("orderId") Long orderId);
}
