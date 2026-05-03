package com.booknest.bookservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.booknest.bookservice.entity.Book;

@DataJpaTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.ANY)
public class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    private Book testBook;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .title("The Great Mocking")
                .author("J.R.R. Token")
                .isbn("1234567890")
                .genre("Fantasy")
                .price(29.99)
                .stock(10)
                .rating(4.8)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Book savedBook = bookRepository.save(testBook);
        Optional<Book> foundBook = bookRepository.findById(savedBook.getBookId());
        
        assertThat(foundBook).isPresent();
        assertThat(foundBook.get().getTitle()).isEqualTo(testBook.getTitle());
    }

    @Test
    void testFindByIsbn() {
        bookRepository.save(testBook);
        Optional<Book> foundBook = bookRepository.findByIsbn("1234567890");
        
        assertThat(foundBook).isPresent();
        assertThat(foundBook.get().getIsbn()).isEqualTo("1234567890");
    }

    @Test
    void testFilterBooksByKeywordAndIsbn() {
        bookRepository.save(testBook);
        Page<Book> result = bookRepository.findFilteredBooks("1234567890", null, null, null, null, Pageable.unpaged());
        
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getIsbn()).isEqualTo("1234567890");
    }

    @Test
    void testFindByGenre() {
        bookRepository.save(testBook);
        Page<Book> result = bookRepository.findByGenreIgnoreCase("fantasy", Pageable.unpaged());
        
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getGenre()).isEqualToIgnoringCase("Fantasy");
    }
}
