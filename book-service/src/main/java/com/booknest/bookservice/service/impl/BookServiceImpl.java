package com.booknest.bookservice.service.impl;

import com.booknest.bookservice.entity.Book;
import com.booknest.bookservice.dto.ReduceStockRequest;
import com.booknest.bookservice.dto.ReduceStockResponse;
import com.booknest.bookservice.exception.InsufficientStockException;
import com.booknest.bookservice.repository.BookRepository;
import com.booknest.bookservice.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Service implementation for managing books and inventory stock.
 * Handles catalog operations, search, filtering, and stock updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    // Adds a new book to the catalog after checking for duplicate ISBNs
    @Override
    @Transactional
    public Book addBook(Book book) {
        if (bookRepository.findByIsbn(book.getIsbn()).isPresent()) {
            throw new RuntimeException("Book with ISBN " + book.getIsbn() + " already exists");
        }
        return bookRepository.save(book);
    }

    // Retrieves a paginated list of all books in the catalog
    @Override
    public Page<Book> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(sanitizePageable(pageable));
    }

    // Fetches the details of a specific book using its ID
    @Override
    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found with id: " + id));
    }

    // Searches for books based on keywords in title, author, or description
    @Override
    public Page<Book> searchBooks(String keyword, Pageable pageable) {
        return bookRepository.searchByKeyword(keyword, sanitizePageable(pageable));
    }

    // Retrieves books filtered by their genre
    @Override
    public Page<Book> getByGenre(String genre, Pageable pageable) {
        return bookRepository.findByGenreIgnoreCase(genre, sanitizePageable(pageable));
    }

    // Filters books based on keyword, genre, price range, and minimum rating
    @Override
    public Page<Book> filterBooks(String keyword, String genre, Double minPrice, Double maxPrice, Double minRating, Pageable pageable) {
        return bookRepository.findFilteredBooks(keyword, genre, minPrice, maxPrice, minRating, sanitizePageable(pageable));
    }

    // Updates the information for an existing book
    @Override
    @Transactional
    public Book updateBook(Book book) {
        if (book.getBookId() == null) {
            throw new RuntimeException("Book ID is required for update");
        }
        Book existingBook = getBookById(book.getBookId());
        
        // Manual copy with null checks to support partial updates and prevent data loss
        if (book.getTitle() != null) existingBook.setTitle(book.getTitle());
        if (book.getAuthor() != null) existingBook.setAuthor(book.getAuthor());
        if (book.getGenre() != null) existingBook.setGenre(book.getGenre());
        if (book.getPublisher() != null) existingBook.setPublisher(book.getPublisher());
        if (book.getPrice() != null) existingBook.setPrice(book.getPrice());
        if (book.getStock() != null) existingBook.setStock(book.getStock());
        if (book.getDescription() != null) existingBook.setDescription(book.getDescription());
        if (book.getCoverImageUrl() != null) existingBook.setCoverImageUrl(book.getCoverImageUrl());
        if (book.getPublishedDate() != null) existingBook.setPublishedDate(book.getPublishedDate());
        
        if (book.getRating() != null) {
            existingBook.setRating(book.getRating());
        }
        if (book.getIsFeatured() != null) {
            existingBook.setIsFeatured(book.getIsFeatured());
        }

        return bookRepository.save(existingBook);
    }

    // Removes a book from the catalog by its ID
    @Override
    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("Book not found with id: " + id);
        }
        bookRepository.deleteById(id);
    }

    // Adjusts the stock count for a specific book
    @Override
    @Transactional
    public Book updateStock(Long id, Integer quantity) {
        Book book = getBookById(id);
        int newStock = book.getStock() + quantity;
        if (newStock < 0) {
            throw new InsufficientStockException("Insufficient stock. Current: " + book.getStock() + ", Requested change: " + quantity);
        }
        book.setStock(newStock);
        return bookRepository.save(book);
    }

    // Retrieves a list of books with a high rating (featured)
    @Override
    public List<Book> getFeaturedBooks() {
        // We only fetch books manually marked as featured
        List<Book> featured = bookRepository.findByIsFeaturedTrue();
        
        // Return only the top 5 to keep the home screen curated
        if (featured.size() > 5) {
            return featured.subList(0, 5);
        }
        return featured;
    }

    // Reduces the stock for a single book, typically called during checkout
    @Override
    @Transactional
    public ReduceStockResponse reduceStock(ReduceStockRequest request) {
        if (request == null || request.getBookId() == null || request.getQuantity() == null) {
            throw new RuntimeException("bookId and quantity are required");
        }
        if (request.getQuantity() <= 0) {
            throw new RuntimeException("quantity must be greater than 0");
        }

        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new RuntimeException("Book not found with id: " + request.getBookId()));

        if (book.getStock() < request.getQuantity()) {
            throw new InsufficientStockException("Insufficient stock");
        }

        book.setStock(book.getStock() - request.getQuantity());
        Book saved = bookRepository.save(book);
        log.info("Stock reduced for bookId {} by {}. New stock: {}", saved.getBookId(), request.getQuantity(), saved.getStock());
        return new ReduceStockResponse(saved.getBookId(), saved.getStock());
    }

    // Reduces stock for multiple books in a single transaction with deadlock protection
    @Override
    @Transactional
    public List<ReduceStockResponse> reduceStockBatch(List<ReduceStockRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new RuntimeException("requests are required");
        }

        // Merge duplicate bookIds, then lock books in stable order to reduce deadlock risk.
        Map<Long, Integer> merged = new HashMap<>();
        for (ReduceStockRequest req : requests) {
            if (req == null || req.getBookId() == null || req.getQuantity() == null) {
                throw new RuntimeException("bookId and quantity are required");
            }
            if (req.getQuantity() <= 0) {
                throw new RuntimeException("quantity must be greater than 0");
            }
            merged.merge(req.getBookId(), req.getQuantity(), Integer::sum);
        }

        List<Long> ids = new ArrayList<>(merged.keySet());
        ids.sort(Comparator.naturalOrder());

        List<Book> lockedBooks = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Book book = bookRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new RuntimeException("Book not found with id: " + id));
            lockedBooks.add(book);
        }

        // Validate all first (same DB transaction), then apply updates.
        for (Book book : lockedBooks) {
            int qty = merged.get(book.getBookId());
            if (book.getStock() < qty) {
                throw new InsufficientStockException("Insufficient stock");
            }
        }

        List<ReduceStockResponse> responses = new ArrayList<>(lockedBooks.size());
        for (Book book : lockedBooks) {
            int qty = merged.get(book.getBookId());
            book.setStock(book.getStock() - qty);
            responses.add(new ReduceStockResponse(book.getBookId(), book.getStock()));
            log.info("Stock reduced for bookId {} by {}. New stock: {}", book.getBookId(), qty, book.getStock());
        }

        bookRepository.saveAll(lockedBooks);
        return responses;
    }

    // Helper to sanitize pagination and sorting parameters
    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable.getSort().isSorted() &&
                pageable.getSort().stream().anyMatch(order -> "string".equals(order.getProperty()))) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return pageable;
    }
}
