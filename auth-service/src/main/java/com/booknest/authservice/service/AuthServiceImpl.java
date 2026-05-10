package com.booknest.authservice.service;

import java.time.LocalDateTime;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.booknest.authservice.dto.LoginRequest;
import com.booknest.authservice.dto.RegisterRequest;
import com.booknest.authservice.entity.User;
import com.booknest.authservice.enums.Role;
import com.booknest.authservice.repository.UserRepository;
import com.booknest.authservice.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Service implementation for user authentication and profile management.
 * This class handles registration, login, password resets, and OAuth integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final com.booknest.authservice.client.WalletClient walletClient;
    private final com.booknest.authservice.repository.PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final RevokedTokenService revokedTokenService;
    private final FileStorageService fileStorageService;

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    // Sends a one-time password (OTP) to the user's email for password reset
    @Override
    @org.springframework.transaction.annotation.Transactional
    public String forgotPassword(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email not registered in our archives");
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        // Delete existing tokens for this email
        tokenRepository.deleteByEmail(email);

        // Save new token
        com.booknest.authservice.entity.PasswordResetToken token = com.booknest.authservice.entity.PasswordResetToken.builder()
                .email(email)
                .token(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        tokenRepository.save(token);

        // Send Email
        emailService.sendOtpEmail(email, otp);

        return "Verification code dispatched to your inbox";
    }

    // Resets the user's password after verifying the provided OTP
    @Override
    @org.springframework.transaction.annotation.Transactional
    public String resetPassword(com.booknest.authservice.dto.ResetPasswordRequest request) {
        com.booknest.authservice.entity.PasswordResetToken resetToken = tokenRepository.findByTokenAndEmail(request.getOtp(), request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Verification code has expired");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new java.util.NoSuchElementException("Account not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Clean up
        tokenRepository.delete(resetToken);

        return "Security vault updated successfully";
    }

    // Registers a new user and initializes their digital wallet
    @Override
    @org.springframework.transaction.annotation.Transactional
    public String register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email address already exists");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .provider(com.booknest.authservice.enums.AuthProvider.LOCAL)
                .mobile(request.getMobile())
                .suspended(false)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = Optional.ofNullable(userRepository.save(user)).orElse(user);

        if (savedUser.getUserId() != null) {
            walletClient.initializeWallet(savedUser.getUserId());
            
            // Dispatch welcome email asynchronously (handled simply here)
            try {
                log.info("Dispatching welcome email to user: {}", savedUser.getEmail());
                emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName());
            } catch (Exception e) {
                log.error("Registration welcome email failure for [{}]: {}", savedUser.getEmail(), e.getMessage(), e);
            }
        }

        return "User registered successfully";
    }

    // Authenticates user credentials and returns a JWT token if successful
    @Override
    public com.booknest.authservice.dto.AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found"));

        if (Boolean.TRUE.equals(user.getSuspended())) {
            throw new IllegalArgumentException("Account is suspended");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        Role finalRole = user.getRole() == null ? Role.USER : user.getRole();
        String accessToken = jwtUtil.generateToken(user.getEmail(), finalRole.name(), user.getUserId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        
        return com.booknest.authservice.dto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Handles user logout (can be extended for token blacklisting)
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (revokedTokenService != null) {
            revokedTokenService.revoke(token, jwtUtil.extractExpiration(token));
        }
    }

    // Checks if a given JWT token is still valid
    @Override
    public boolean validateToken(String token) {
        boolean valid = jwtUtil.validateToken(token);
        if (!valid) {
            return false;
        }
        return revokedTokenService == null || !revokedTokenService.isRevoked(token);
    }

    // Generates a fresh JWT token based on the information in an existing one
    @Override
    public com.booknest.authservice.dto.AuthResponse refreshToken(String token) {
        if (!validateToken(token)) {
            throw new IllegalArgumentException("Token is invalid, expired, or revoked");
        }
        String email = jwtUtil.extractUsername(token);
        String role = jwtUtil.extractRole(token);
        Long userId = jwtUtil.extractUserId(token);
        // If userId is missing (old token), load it from DB before re-embedding
        if (userId == null) {
            userId = userRepository.findByEmail(email).map(u -> u.getUserId()).orElse(null);
        }
        
        String accessToken = jwtUtil.generateToken(email, role, userId);
        String refreshToken = jwtUtil.generateRefreshToken(email);
        
        return com.booknest.authservice.dto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Updates the password for an already authenticated user
    @Override
    public String changePassword(String email, com.booknest.authservice.dto.ChangePasswordRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "Password updated successfully";
    }

    // Manages login and registration for OAuth-based authentication (e.g., Google, GitHub)
    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.booknest.authservice.dto.AuthResponse handleOAuthLogin(String email, String fullName, com.booknest.authservice.enums.AuthProvider provider) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(fullName)
                    .provider(provider)
                    .role(Role.USER)
                    .suspended(false)
                    .createdAt(LocalDateTime.now())
                    .passwordHash("OAUTH_USER") // Placeholder for OAuth users to satisfy DB constraint
                    .build();
            User saved = Optional.ofNullable(userRepository.save(newUser)).orElse(newUser);
            
            // Initialize Wallet and send welcome email for new OAuth user
            if (saved.getUserId() != null) {
                walletClient.initializeWallet(saved.getUserId());
                try {
                    emailService.sendWelcomeEmail(saved.getEmail(), saved.getFullName());
                } catch (Exception e) {
                    log.warn("OAuth welcome email failed for user [{}]: {}", saved.getEmail(), e.getMessage());
                }
            }
            
            return saved;
        });

        if (user.getProvider() != null && !user.getProvider().equals(provider)) {
            throw new IllegalArgumentException("Account is registered with a different login provider: " + user.getProvider());
        }

        if (Boolean.TRUE.equals(user.getSuspended())) {
            throw new IllegalArgumentException("Account is suspended");
        }

        Role finalRole = user.getRole() == null ? Role.USER : user.getRole();
        String accessToken = jwtUtil.generateToken(user.getEmail(), finalRole.name(), user.getUserId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        
        return com.booknest.authservice.dto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Retrieves the profile information for a specific user
    @Override
    public com.booknest.authservice.dto.UserResponse getUserProfile(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        return mapToUserResponse(user);
    }

    @Override
    public String uploadProfileImage(Long userId, org.springframework.web.multipart.MultipartFile file) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        
        String imageUrl = fileStorageService.save(file);
        user.setProfileImage(imageUrl);
        userRepository.save(user);
        return imageUrl;
    }

    // Fetches a list of all registered users in the system
    @Override
    public java.util.List<com.booknest.authservice.dto.UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.booknest.authservice.dto.UserResponse updateUserRole(Long userId, Role role) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        user.setRole(role);
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.booknest.authservice.dto.UserResponse updateSuspendedStatus(Long userId, boolean suspended) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        user.setSuspended(suspended);
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteUser(Long userId) {
        userRepository.findByUserId(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        userRepository.deleteByUserId(userId);
    }

    // Updates the profile details (name and image) for a user
    @Override
    public com.booknest.authservice.dto.UserResponse updateProfile(String email, String name, String profileImage) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));

        user.setFullName(name.trim());
        if (profileImage != null && !profileImage.trim().isEmpty()) {
            user.setProfileImage(profileImage);
        }
        userRepository.save(user);
        return mapToUserResponse(user);
    }

    // Helper method to convert a User entity into a UserResponse DTO
    private com.booknest.authservice.dto.UserResponse mapToUserResponse(User user) {
        return com.booknest.authservice.dto.UserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .provider(user.getProvider())
                .mobile(user.getMobile())
                .profileImage(user.getProfileImage())
                .suspended(Boolean.TRUE.equals(user.getSuspended()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
