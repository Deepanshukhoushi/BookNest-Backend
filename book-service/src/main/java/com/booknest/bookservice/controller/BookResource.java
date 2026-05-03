package com.booknest.bookservice.controller;

import com.booknest.bookservice.dto.BookRequest;
import com.booknest.bookservice.entity.Book;
import com.booknest.bookservice.service.BookService;
import com.booknest.bookservice.payload.ApiResponse;
import com.booknest.bookservice.dto.ReduceStockRequest;
import com.booknest.bookservice.dto.ReduceStockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import com.booknest.bookservice.service.FileStorageService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * REST controller for managing books, inventory, and file uploads.
 * Provides endpoints for CRUD operations and stock management.
 *
 * <p>Write operations (POST/PUT) accept a {@link BookRequest} DTO rather than the raw
 * {@link Book} entity, enforcing DTO separation to prevent over-posting attacks.
 */
@RestController
@RequestMapping("/api/v1/books")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BookResource {

    private final BookService bookService;
    private final FileStorageService fileStorageService;

    // Adds a new book to the catalog — accepts BookRequest DTO, not the raw entity
    @PostMapping
    public ResponseEntity<ApiResponse<Book>> addBook(@Valid @RequestBody BookRequest request) {
        Book book = mapToEntity(request);
        return new ResponseEntity<>(new ApiResponse<>(true, "Book added successfully", bookService.addBook(book)), HttpStatus.CREATED);
    }

    // Retrieves a paginated list of all available books
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Book>>> getAllBooks(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Books fetched successfully", bookService.getAllBooks(pageable)));
    }

    // Fetches detailed information for a single book by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Book>> getBookById(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book fetched successfully", bookService.getBookById(id)));
    }

    // Searches and filters books using various criteria
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Book>>> searchBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Books fetched successfully",
                bookService.filterBooks(keyword, genre, minPrice, maxPrice, minRating, pageable)));
    }

    // Retrieves books belonging to a specific genre
    @GetMapping("/genre")
    public ResponseEntity<ApiResponse<Page<Book>>> getByGenre(@RequestParam String genre, Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Books fetched successfully", bookService.getByGenre(genre, pageable)));
    }

    // Updates details for an existing book — accepts BookRequest DTO plus the path ID
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Book>> updateBook(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        Book book = mapToEntity(request);
        book.setBookId(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book updated successfully", bookService.updateBook(book)));
    }

    // Removes a book from the catalog
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Book deleted successfully", null));
    }

    // Manually adjusts the stock quantity of a book
    @PutMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<Book>> updateStock(@PathVariable Long id, @RequestParam Integer quantity) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book stock updated successfully", bookService.updateStock(id, quantity)));
    }

    // Decreases stock for a single book after a purchase
    @PutMapping("/reduce-stock")
    public ResponseEntity<ApiResponse<ReduceStockResponse>> reduceStock(@RequestBody ReduceStockRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book stock reduced successfully", bookService.reduceStock(request)));
    }

    // Decreases stock for multiple books at once (batch operation)
    @PutMapping("/reduce-stock/batch")
    public ResponseEntity<ApiResponse<List<ReduceStockResponse>>> reduceStockBatch(@RequestBody List<ReduceStockRequest> requests) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Book stock reduced successfully", bookService.reduceStockBatch(requests)));
    }

    // Returns a curated list of highly-rated featured books
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<Book>>> getFeaturedBooks() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Featured books fetched successfully", bookService.getFeaturedBooks()));
    }

    // Uploads a file such as a book cover image
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file);
        Map<String, String> response = new HashMap<>();
        response.put("fileUrl", fileUrl);
        return ResponseEntity.ok(new ApiResponse<>(true, "File uploaded successfully", response));
    }

    // Downloads a previously uploaded file by filename
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        Resource resource = fileStorageService.loadFileAsResource(fileName);
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (Exception ex) {
            // Fall through to default content type
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Maps a {@link BookRequest} DTO to a {@link Book} entity.
     * Fields that must not be set by the caller (bookId, rating, isFeatured)
     * are intentionally excluded and left to the service / database layer.
     */
    private Book mapToEntity(BookRequest req) {
        return Book.builder()
                .title(req.getTitle())
                .author(req.getAuthor())
                .isbn(req.getIsbn())
                .genre(req.getGenre())
                .publisher(req.getPublisher())
                .price(req.getPrice())
                .stock(req.getStock())
                .description(req.getDescription())
                .coverImageUrl(req.getCoverImageUrl())
                .publishedDate(req.getPublishedDate())
                .isFeatured(req.getIsFeatured())
                .build();
    }
}
