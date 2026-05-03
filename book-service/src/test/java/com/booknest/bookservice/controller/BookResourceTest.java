package com.booknest.bookservice.controller;

import com.booknest.bookservice.dto.BookRequest;

import com.booknest.bookservice.entity.Book;
import com.booknest.bookservice.security.JwtAuthenticationFilter;
import com.booknest.bookservice.service.BookService;
import com.booknest.bookservice.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookResource.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security for controller tests
public class BookResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private Book testBook;
    private BookRequest testRequest;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .bookId(1L)
                .title("Controller Test Book")
                .isbn("111222333")
                .price(20.0)
                .stock(10)
                .author("Author Name")
                .build();

        testRequest = new BookRequest();
        testRequest.setTitle("Controller Test Book");
        testRequest.setIsbn("111222333");
        testRequest.setPrice(20.0);
        testRequest.setStock(10);
        testRequest.setAuthor("Author Name");
    }

    @Test
    void testAddBook() throws Exception {
        when(bookService.addBook(any(Book.class))).thenReturn(testBook);

        mockMvc.perform(post("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Controller Test Book"));
    }

    @Test
    void testGetBookById() throws Exception {
        when(bookService.getBookById(1L)).thenReturn(testBook);

        mockMvc.perform(get("/api/v1/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Controller Test Book"));
    }

    @Test
    void testGetAllBooks() throws Exception {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookService.getAllBooks(any(Pageable.class))).thenReturn(bookPage);

        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Controller Test Book"));
    }

    @Test
    void testSearchBooks() throws Exception {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookService.filterBooks(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(bookPage);

        mockMvc.perform(get("/api/v1/books/search").param("keyword", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Controller Test Book"));
    }

    @Test
    void testDeleteBook() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Book deleted successfully"));
    }

    @Test
    void testUpdateBook() throws Exception {
        when(bookService.updateBook(any(Book.class))).thenReturn(testBook);

        mockMvc.perform(put("/api/v1/books/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Controller Test Book"));
    }

    @Test
    void testUpdateStock() throws Exception {
        when(bookService.updateStock(anyLong(), anyInt())).thenReturn(testBook);

        mockMvc.perform(put("/api/v1/books/1/stock").param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetFeaturedBooks() throws Exception {
        when(bookService.getFeaturedBooks()).thenReturn(Collections.singletonList(testBook));

        mockMvc.perform(get("/api/v1/books/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Controller Test Book"));
    }

    @Test
    void testGetByGenre() throws Exception {
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(testBook));
        when(bookService.getByGenre(anyString(), any(Pageable.class))).thenReturn(bookPage);

        mockMvc.perform(get("/api/v1/books/genre").param("genre", "Fantasy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testReduceStockBatch() throws Exception {
        when(bookService.reduceStockBatch(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(put("/api/v1/books/reduce-stock/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"bookId\":1, \"quantity\":1}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testReduceStock() throws Exception {
        when(bookService.reduceStock(any())).thenReturn(null);

        mockMvc.perform(put("/api/v1/books/reduce-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":1, \"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testUploadFile() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, "test".getBytes());
        when(fileStorageService.storeFile(any())).thenReturn("http://test.com/test.jpg");

        mockMvc.perform(multipart("/api/v1/books/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl").value("http://test.com/test.jpg"));
    }

    @Test
    void testDownloadFile() throws Exception {
        org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource("test".getBytes()) {
            @Override
            public String getFilename() { return "test.jpg"; }
        };
        when(fileStorageService.loadFileAsResource("test.jpg")).thenReturn(resource);

        mockMvc.perform(get("/api/v1/books/download/test.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.jpg\""));
    }
}
