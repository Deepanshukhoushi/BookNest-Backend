package com.booknest.authservice.controller;

import com.booknest.authservice.dto.ChangePasswordRequest;
import com.booknest.authservice.dto.UpdateProfileRequest;
import com.booknest.authservice.dto.UserResponse;
import com.booknest.authservice.payload.ApiResponse;
import com.booknest.authservice.security.JwtUtil;
import com.booknest.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing user profile and security settings.
 * Handles profile updates and password changes for authenticated users.
 *
 * <p>All write endpoints use {@code @Valid} to enforce DTO validation annotations
 * before the request reaches the service layer.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    // Updates the display name and optional profile image URL for the current user
    @PutMapping("/update-profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

        String token = extractBearerToken(authorizationHeader);
        String email = jwtUtil.extractUsername(token);
        UserResponse updatedUser = authService.updateProfile(email, request.getName(), request.getProfileImage());
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile updated successfully", updatedUser));
    }

    // Handles profile image upload for the current user
    @PostMapping(value = "/upload-profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

        String token = extractBearerToken(authorizationHeader);
        Long userId = jwtUtil.extractUserId(token);
        String imageUrl = authService.uploadProfileImage(userId, file);
        return ResponseEntity.ok(new ApiResponse<>(true, "Image uploaded successfully", imageUrl));
    }

    // Handles secure password changes for the authenticated user
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

        String token = extractBearerToken(authorizationHeader);
        String email = jwtUtil.extractUsername(token);
        String result = authService.changePassword(email, request);
        return ResponseEntity.ok(new ApiResponse<>(true, result, null));
    }

    // Extracts the JWT token from the Bearer authorization header
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing or invalid authorization header");
        }
        return authorizationHeader.substring(7);
    }
}
