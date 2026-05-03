package com.booknest.bookservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        fileStorageService = new FileStorageService();
    }

    @Test
    void storeFile_AndLoadFileAsResource_WorkTogether() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", "cover-data".getBytes());

        String fileUrl = fileStorageService.storeFile(file);
        String storedFileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
        Path storedFile = Paths.get("uploads").toAbsolutePath().normalize().resolve(storedFileName);

        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(fileUrl).contains("/api/v1/books/download/");
        assertThat(fileStorageService.loadFileAsResource(storedFileName).exists()).isTrue();
    }

    @Test
    void loadFileAsResource_WhenMissing_ThrowsMeaningfulError() {
        assertThatThrownBy(() -> fileStorageService.loadFileAsResource("missing-file.png"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void storeFile_WhenFilenameIsNull_UsesDefaultName() {
        MockMultipartFile file = new MockMultipartFile("file", null, "image/png", "data".getBytes());
        String fileUrl = fileStorageService.storeFile(file);
        assertThat(fileUrl).contains("image.jpg");
    }

    @Test
    void storeFile_WhenFilenameContainsPathTraversal_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "../danger.png", "image/png", "data".getBytes());
        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("invalid path sequence");
    }

    @Test
    void storeFile_WhenFileIsEmpty_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Book cover image file is required");
    }

    @Test
    void storeFile_WhenFileSizeExceedsLimit_ThrowsException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeContent);
        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB or smaller");
    }

    @Test
    void storeFile_WhenContentTypeIsInvalid_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
        assertThatThrownBy(() -> fileStorageService.storeFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPG, PNG, and WEBP");
    }
}
