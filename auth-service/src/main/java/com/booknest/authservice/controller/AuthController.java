package com.booknest.authservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.booknest.authservice.dto.LoginRequest;
import com.booknest.authservice.dto.RegisterRequest;
import com.booknest.authservice.payload.ApiResponse;
import com.booknest.authservice.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

/**
 * REST controller for handling authentication-related requests like login, registration, and password management.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;

	// Handles new user registration
	@PostMapping("/register")
	public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(true, "User registered successfully", authService.register(request)));
	}

	// Handles user login and returns a JWT token if authentication succeeds
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", authService.login(request)));
	}
	
	// Refreshes an existing JWT token to extend the user's session
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<String>> refresh(@RequestParam String token){
		return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed successfully", authService.refreshToken(token)));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		authService.logout(token);
		return ResponseEntity.ok(new ApiResponse<>(true, "Logout successful", null));
	}

	@GetMapping("/validate")
	public ResponseEntity<ApiResponse<Boolean>> validate(@RequestParam String token) {
		boolean valid = authService.validateToken(token);
		if (!valid) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked token");
		}
		return ResponseEntity.ok(new ApiResponse<>(true, "Token is valid", true));
	}

	// Retrieves the profile information for a specific user by their ID
	@GetMapping("/profile/{userId}")
	public ResponseEntity<ApiResponse<com.booknest.authservice.dto.UserResponse>> getProfile(@PathVariable Long userId, HttpServletRequest request) {
		enforceUserAccess(userId, request);
		return ResponseEntity.ok(new ApiResponse<>(true, "Profile fetched successfully", authService.getUserProfile(userId)));
	}

	/**
	 * Backward-compatible alias for internal clients that still call /user/{id}.
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<ApiResponse<com.booknest.authservice.dto.UserResponse>> getUserById(@PathVariable Long userId, HttpServletRequest request) {
		enforceUserAccess(userId, request);
		return ResponseEntity.ok(new ApiResponse<>(true, "Profile fetched successfully", authService.getUserProfile(userId)));
	}

	/**
	 * ADMIN-only endpoint: returns all users.
	 * Protected at both URL-matcher level (SecurityConfig) and method level (@PreAuthorize) for defense-in-depth.
	 */
	@GetMapping("/all")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<List<com.booknest.authservice.dto.UserResponse>>> getAllUsers() {
		return ResponseEntity.ok(new ApiResponse<>(true, "Users fetched successfully", authService.getAllUsers()));
	}

	@PutMapping("/users/{userId}/role")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<com.booknest.authservice.dto.UserResponse>> updateUserRole(
			@PathVariable Long userId,
			@Valid @RequestBody com.booknest.authservice.dto.UpdateUserRoleRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(true, "User role updated successfully", authService.updateUserRole(userId, request.getRole())));
	}

	@PutMapping("/users/{userId}/suspend")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<com.booknest.authservice.dto.UserResponse>> suspendUser(@PathVariable Long userId) {
		return ResponseEntity.ok(new ApiResponse<>(true, "User suspended successfully", authService.updateSuspendedStatus(userId, true)));
	}

	@PutMapping("/users/{userId}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<com.booknest.authservice.dto.UserResponse>> reactivateUser(@PathVariable Long userId) {
		return ResponseEntity.ok(new ApiResponse<>(true, "User reactivated successfully", authService.updateSuspendedStatus(userId, false)));
	}

	@DeleteMapping("/users/{userId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
		authService.deleteUser(userId);
		return ResponseEntity.ok(new ApiResponse<>(true, "User deleted successfully", null));
	}

	// Allows a logged-in user to change their password
	@PostMapping("/change-password")
	public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody com.booknest.authservice.dto.ChangePasswordRequest request) {
		String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
		return ResponseEntity.ok(new ApiResponse<>(true, "Password changed successfully", authService.changePassword(email, request)));
	}

	// Initiates the password recovery process by sending an OTP to the user's email
	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam String email) {
		return ResponseEntity.ok(new ApiResponse<>(true, "Verification process initiated", authService.forgotPassword(email)));
	}

	// Completes the password reset process using the OTP received via email
	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody com.booknest.authservice.dto.ResetPasswordRequest request) {
		return ResponseEntity.ok(new ApiResponse<>(true, "Security reset successful", authService.resetPassword(request)));
	}

	private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
		if (targetUserId == null || request == null) {
			return;
		}

		String roleHeader = resolveRole(request);
		if (isAdmin(roleHeader)) {
			return;
		}

		Long authenticatedUserId = resolveUserId(request);
		if (authenticatedUserId == null) {
			return;
		}

		if (!authenticatedUserId.equals(targetUserId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
		}
	}

	private String resolveRole(HttpServletRequest request) {
		Object attribute = request.getAttribute("authenticatedRole");
		if (attribute instanceof String role && !role.isBlank()) {
			return role;
		}
		return request.getHeader("X-Auth-Role");
	}

	private Long resolveUserId(HttpServletRequest request) {
		Object attribute = request.getAttribute("authenticatedUserId");
		if (attribute instanceof Long userId) {
			return userId;
		}
		if (attribute instanceof Integer userId) {
			return userId.longValue();
		}

		String userIdHeader = request.getHeader("X-Auth-UserId");
		if (userIdHeader == null || userIdHeader.isBlank()) {
			return null;
		}

		try {
			return Long.valueOf(userIdHeader);
		} catch (NumberFormatException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
		}
	}

	private boolean isAdmin(String roleHeader) {
		if (roleHeader == null || roleHeader.isBlank()) {
			return false;
		}
		String normalized = roleHeader.trim().toUpperCase();
		if (normalized.startsWith("ROLE_")) {
			normalized = normalized.substring("ROLE_".length());
		}
		return "ADMIN".equals(normalized);
	}

	private String extractBearerToken(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
		}
		return authorizationHeader.substring(7);
	}
}
