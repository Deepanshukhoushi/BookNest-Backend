package com.booknest.notificationservice.client;

import com.booknest.notificationservice.config.InternalFeignConfig;
import com.booknest.notificationservice.dto.ApiResponse;
import com.booknest.notificationservice.dto.BookDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "book-service", path = "/api/v1/books", configuration = InternalFeignConfig.class)
public interface BookClient {

    @GetMapping("/{id}")
    ApiResponse<BookDTO> getBookById(@PathVariable("id") Long id);
}
