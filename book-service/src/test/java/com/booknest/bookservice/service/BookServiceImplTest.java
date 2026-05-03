package com.booknest.bookservice.service;

import com.booknest.bookservice.dto.ReduceStockRequest;
import com.booknest.bookservice.dto.ReduceStockResponse;
import com.booknest.bookservice.entity.Book;
import com.booknest.bookservice.exception.InsufficientStockException;
import com.booknest.bookservice.repository.BookRepository;
import com.booknest.bookservice.service.impl.BookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    private Book testBook;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .bookId(1L)
                .title("Service Test Book")
                .author("Author")
                .isbn("9999999999")
                .genre("Fiction")
                .stock(5)
                .price(15.0)
                .isFeatured(true)
                .build();
    }

    @Test
    void testAddBook_Success() {
        when(bookRepository.findByIsbn(anyString())).thenReturn(Optional.empty());
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        Book result = bookService.addBook(testBook);

        assertThat(result).isNotNull();
        verify(bookRepository).save(testBook);
    }

    @Test
    void testAddBook_DuplicateIsbn() {
        when(bookRepository.findByIsbn(anyString())).thenReturn(Optional.of(testBook));

        assertThatThrownBy(() -> bookService.addBook(testBook))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testGetAllBooks() {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(bookPage);

        Page<Book> result = bookService.getAllBooks(PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
        verify(bookRepository).findAll(any(Pageable.class));
    }

    @Test
    void testGetBookById_Success() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        Book result = bookService.getBookById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getBookId()).isEqualTo(1L);
    }

    @Test
    void testGetBookById_NotFound() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookById(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testSearchBooks() {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookRepository.searchByKeyword(anyString(), any(Pageable.class))).thenReturn(bookPage);

        Page<Book> result = bookService.searchBooks("test", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void testUpdateBook_PartialUpdate() {
        Book updateRequest = Book.builder().bookId(1L).title("Updated Title").build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        Book result = bookService.updateBook(updateRequest);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getAuthor()).isEqualTo("Author"); // Should remain unchanged
    }

    @Test
    void testDeleteBook_Success() {
        when(bookRepository.existsById(1L)).thenReturn(true);
        doNothing().when(bookRepository).deleteById(1L);

        bookService.deleteBook(1L);

        verify(bookRepository).deleteById(1L);
    }

    @Test
    void testDeleteBook_NotFound() {
        when(bookRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> bookService.deleteBook(1L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testUpdateStock_Success() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        Book result = bookService.updateStock(1L, 5);

        assertThat(result.getStock()).isEqualTo(10);
    }

    @Test
    void testUpdateStock_InsufficientStock() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        assertThatThrownBy(() -> bookService.updateStock(1L, -10))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void testGetFeaturedBooks_LimitCheck() {
        List<Book> featuredList = Arrays.asList(testBook, testBook, testBook, testBook, testBook, testBook);
        when(bookRepository.findByIsFeaturedTrue()).thenReturn(featuredList);

        List<Book> result = bookService.getFeaturedBooks();

        assertThat(result).hasSize(5); // Should be limited to 5
    }

    @Test
    void testReduceStock_Success() {
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        ReduceStockResponse result = bookService.reduceStock(new ReduceStockRequest(1L, 2));

        assertThat(result.getNewStock()).isEqualTo(3);
    }

    @Test
    void testReduceStockBatch_Success() {
        Book book2 = Book.builder().bookId(2L).stock(10).build();
        
        when(bookRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(book2));
        
        List<ReduceStockRequest> requests = Arrays.asList(
                new ReduceStockRequest(1L, 1),
                new ReduceStockRequest(2L, 5),
                new ReduceStockRequest(1L, 2) // Duplicate ID to test merging
        );

        List<ReduceStockResponse> results = bookService.reduceStockBatch(requests);

        assertThat(results).hasSize(2);
        // Book 1: 5 - (1+2) = 2
        assertThat(results.stream().filter(r -> r.getBookId() == 1L).findFirst().get().getNewStock()).isEqualTo(2);
        // Book 2: 10 - 5 = 5
        assertThat(results.stream().filter(r -> r.getBookId() == 2L).findFirst().get().getNewStock()).isEqualTo(5);
    }

    @Test
    void testGetByGenre() {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookRepository.findByGenreIgnoreCase(anyString(), any(Pageable.class))).thenReturn(bookPage);

        Page<Book> result = bookService.getByGenre("Fiction", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(bookRepository).findByGenreIgnoreCase(eq("Fiction"), any(Pageable.class));
    }

    @Test
    void testFilterBooks() {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookRepository.findFilteredBooks(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(Pageable.class)))
                .thenReturn(bookPage);

        Page<Book> result = bookService.filterBooks("test", "Fiction", 10.0, 20.0, 4.0, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void testUpdateBook_NotFound() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bookService.updateBook(testBook))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testUpdateBook_NullId() {
        Book bookWithNullId = Book.builder().title("No ID").build();
        assertThatThrownBy(() -> bookService.updateBook(bookWithNullId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ID is required");
    }

    @Test
    void testReduceStockBatch_EmptyRequests() {
        assertThatThrownBy(() -> bookService.reduceStockBatch(null))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> bookService.reduceStockBatch(Collections.emptyList()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testSanitizePageable_InvalidSort() {
        // Test branch coverage for sanitizePageable
        Pageable pageable = PageRequest.of(0, 10, Sort.by("string"));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));

        bookService.getAllBooks(pageable);

        verify(bookRepository).findAll(argThat((Pageable p) -> !p.getSort().isSorted()));
    }
}
