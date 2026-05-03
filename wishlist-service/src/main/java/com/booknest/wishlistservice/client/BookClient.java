package com.booknest.wishlistservice.client;

import com.booknest.wishlistservice.dto.BookDTO;
import com.booknest.wishlistservice.payload.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "BOOK-SERVICE")
public interface BookClient {

    @GetMapping("/api/v1/books/{id}")
    ApiResponse<BookDTO> getBookById(@PathVariable("id") Long id);
}
