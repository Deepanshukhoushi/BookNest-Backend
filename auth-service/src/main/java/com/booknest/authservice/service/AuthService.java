package com.booknest.authservice.service;

import com.booknest.authservice.dto.LoginRequest;
import com.booknest.authservice.dto.RegisterRequest;
import com.booknest.authservice.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface AuthService {
	String register(RegisterRequest request);
	String login(LoginRequest request);
	void logout(String token);
	boolean validateToken(String token);
	String refreshToken(String token);
	String changePassword(String email, com.booknest.authservice.dto.ChangePasswordRequest request);
	String handleOAuthLogin(String email, String fullName, com.booknest.authservice.enums.AuthProvider provider);
	UserResponse getUserProfile(Long userId);
    String uploadProfileImage(Long userId, MultipartFile file);
	List<UserResponse> getAllUsers();
	UserResponse updateUserRole(Long userId, com.booknest.authservice.enums.Role role);
	UserResponse updateSuspendedStatus(Long userId, boolean suspended);
	void deleteUser(Long userId);
	UserResponse updateProfile(String email, String name, String profileImage);
	String forgotPassword(String email);
	String resetPassword(com.booknest.authservice.dto.ResetPasswordRequest request);
}
