package com.booknest.authservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private final FileStorageService fileStorageService = new FileStorageService();

    @Test
    void save_StoresMultipartFileAndReturnsPublicPath() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "avatar-content".getBytes());

        String savedPath = fileStorageService.save(file);

        assertThat(savedPath).startsWith("/uploads/profiles/");
        Path storedFile = Paths.get("uploads/profiles", savedPath.substring("/uploads/profiles/".length()));
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readString(storedFile)).isEqualTo("avatar-content");
    }

    @Test
    void save_WhenFilenameIsNull_UsesDefaultName() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null, // null filename
                "image/png",
                "content".getBytes());

        String savedPath = fileStorageService.save(file);
        assertThat(savedPath).contains("profile-image");
    }

    @Test
    void save_WhenFilenameContainsPathTraversal_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../danger.png",
                "image/png",
                "content".getBytes());

        assertThatThrownBy(() -> fileStorageService.save(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file name");
    }

    @Test
    void save_WhenFileIsEmpty_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> fileStorageService.save(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Profile image file is required");
    }

    @Test
    void save_WhenFileSizeExceedsLimit_ThrowsException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", largeContent);
        assertThatThrownBy(() -> fileStorageService.save(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB or smaller");
    }

    @Test
    void save_WhenContentTypeIsInvalid_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        assertThatThrownBy(() -> fileStorageService.save(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only JPG, PNG, and WEBP");
    }
}
