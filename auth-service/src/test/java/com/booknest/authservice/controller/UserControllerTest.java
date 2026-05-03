package com.booknest.authservice.controller;

import com.booknest.authservice.dto.ChangePasswordRequest;
import com.booknest.authservice.dto.UpdateProfileRequest;
import com.booknest.authservice.dto.UserResponse;
import com.booknest.authservice.security.JwtUtil;
import com.booknest.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper = new ObjectMapper();
    private final String token = "Bearer mock-token";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new com.booknest.authservice.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void testUpdateProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("New Name");
        request.setProfileImage("new-image.jpg");

        UserResponse response = UserResponse.builder().fullName("New Name").build();

        when(jwtUtil.extractUsername(anyString())).thenReturn("test@example.com");
        when(authService.updateProfile(eq("test@example.com"), eq("New Name"), eq("new-image.jpg"))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/update-profile")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("New Name"));
    }

    @Test
    void testChangePassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPass");
        request.setNewPassword("newPassword123");

        when(jwtUtil.extractUsername(anyString())).thenReturn("test@example.com");
        when(authService.changePassword(eq("test@example.com"), any())).thenReturn("Success");

        mockMvc.perform(post("/api/v1/users/change-password")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    @Test
    void testUploadProfileImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "avatar".getBytes());

        when(jwtUtil.extractUserId("mock-token")).thenReturn(1L);
        when(authService.uploadProfileImage(eq(1L), any())).thenReturn("/uploads/profiles/avatar.png");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/users/upload-profile-image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("/uploads/profiles/avatar.png"));
    }

    @Test
    void testUpdateProfile_InvalidAuthorizationHeader() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("New Name");

        mockMvc.perform(put("/api/v1/users/update-profile")
                .header(HttpHeaders.AUTHORIZATION, "Token bad")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
