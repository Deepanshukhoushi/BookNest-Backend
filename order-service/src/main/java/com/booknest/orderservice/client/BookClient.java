package com.booknest.orderservice.client;

import com.booknest.orderservice.dto.BookDTO;
import com.booknest.orderservice.dto.ReduceStockRequest;
import com.booknest.orderservice.payload.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "BOOK-SERVICE")
public interface BookClient {

    @GetMapping("/api/v1/books/{id}")
    ApiResponse<BookDTO> getBookById(@PathVariable("id") Long id);

    @PutMapping("/api/v1/books/reduce-stock")
    ApiResponse<Object> reduceStock(@RequestBody ReduceStockRequest request);

    @PutMapping("/api/v1/books/reduce-stock/batch")
    ApiResponse<Object> reduceStockBatch(@RequestBody List<ReduceStockRequest> requests);

    @PutMapping("/api/v1/books/{id}/stock")
    ApiResponse<BookDTO> updateStock(@PathVariable("id") Long id, @RequestParam("quantity") Integer quantity);
}
