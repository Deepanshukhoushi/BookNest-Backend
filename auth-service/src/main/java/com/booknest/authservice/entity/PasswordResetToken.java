package com.booknest.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing a temporary security token (OTP) for password resets.
 * Stores the token value, associated user email, and expiration timestamp.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String token; // This will store the OTP

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    // Determines if the token is still valid or has reached its expiration
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}
