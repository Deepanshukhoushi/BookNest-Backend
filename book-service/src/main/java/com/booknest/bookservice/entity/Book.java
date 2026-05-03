package com.booknest.bookservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "books", indexes = {
    @Index(name = "idx_book_title", columnList = "title"),
    @Index(name = "idx_book_author", columnList = "author"),
    @Index(name = "idx_book_isbn", columnList = "isbn")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a book in the system catalog.
 * Stores comprehensive metadata including title, author, price, and inventory levels.
 */
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookId;

    @NotBlank(message = "Title is mandatory")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Author is mandatory")
    @Column(nullable = false)
    private String author;

    @NotBlank(message = "ISBN is mandatory")
    @Column(unique = true, nullable = false)
    private String isbn;

    private String genre;

    private String publisher;

    @NotNull(message = "Price is mandatory")
    @Min(value = 0, message = "Price must be greater than or equal to 0")
    @Column(nullable = false)
    private Double price;

    @NotNull(message = "Stock is mandatory")
    @Min(value = 0, message = "Stock must be greater than or equal to 0")
    @Column(nullable = false)
    private Integer stock;

    private Double rating;

    @Column(length = 1000)
    private String description;

    private String coverImageUrl;

    private LocalDate publishedDate;
    
    @Builder.Default
    private Boolean isFeatured = false;
}
