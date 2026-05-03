package com.booknest.bookservice.service;

import com.booknest.bookservice.entity.Book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import com.booknest.bookservice.dto.ReduceStockRequest;
import com.booknest.bookservice.dto.ReduceStockResponse;

public interface BookService {
    Book addBook(Book book);
    Page<Book> getAllBooks(Pageable pageable);
    Book getBookById(Long id);
    Page<Book> searchBooks(String keyword, Pageable pageable);
    Page<Book> getByGenre(String genre, Pageable pageable);
    Page<Book> filterBooks(String keyword, String genre, Double minPrice, Double maxPrice, Double minRating, Pageable pageable);
    Book updateBook(Book book);
    void deleteBook(Long id);
    Book updateStock(Long id, Integer quantity);
    List<Book> getFeaturedBooks();

    ReduceStockResponse reduceStock(ReduceStockRequest request);
    List<ReduceStockResponse> reduceStockBatch(List<ReduceStockRequest> requests);
}
