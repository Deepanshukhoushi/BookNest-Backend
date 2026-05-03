package com.booknest.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating a user's display name and optional profile image URL.
 * Both fields are validated to prevent blank or oversized values from reaching the service layer.
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[A-Z][a-z]+(\\s[A-Z][a-z]+)*$", message = "Each word in name must start with a capital letter")
    private String name;

    // Optional — a URL string; no format enforcement here (validated by service if needed)
    @Size(max = 500, message = "Profile image URL must not exceed 500 characters")
    private String profileImage;
}
