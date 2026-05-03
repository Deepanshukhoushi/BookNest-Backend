package com.booknest.authservice.dto;

import java.time.LocalDateTime;

import com.booknest.authservice.enums.AuthProvider;
import com.booknest.authservice.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long userId;
    private String fullName;
    private String email;
    private Role role;
    private AuthProvider provider;
    private String mobile;
    private String profileImage;
    private Boolean suspended;
    private LocalDateTime createdAt;
}
