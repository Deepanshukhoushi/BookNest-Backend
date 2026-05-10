package com.booknest.authservice.service;

import com.booknest.authservice.client.WalletClient;
import com.booknest.authservice.dto.*;
import com.booknest.authservice.entity.PasswordResetToken;
import com.booknest.authservice.entity.User;
import com.booknest.authservice.enums.AuthProvider;
import com.booknest.authservice.enums.Role;
import com.booknest.authservice.repository.PasswordResetTokenRepository;
import com.booknest.authservice.repository.UserRepository;
import com.booknest.authservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private WalletClient walletClient;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private RevokedTokenService revokedTokenService;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("test@example.com")
                .fullName("Test User")
                .passwordHash("hashedPass")
                .role(Role.USER)
                .suspended(false)
                .provider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setMobile("1234567890");
        
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPass");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        String result = authService.register(request);

        assertThat(result).isEqualTo("User registered successfully");
        verify(walletClient).initializeWallet(1L);
        verify(emailService).sendWelcomeEmail(eq("test@example.com"), anyString());
    }

    @Test
    void testLogin_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "hashedPass")).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong())).thenReturn("mock-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("mock-refresh-token");

        AuthResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("mock-token");
        assertThat(result.getRefreshToken()).isEqualTo("mock-refresh-token");
    }

    @Test
    void testLogin_Suspended() {
        testUser.setSuspended(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void testForgotPassword_Success() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);
        
        authService.forgotPassword("test@example.com");

        verify(tokenRepository).deleteByEmail("test@example.com");
        var captor = forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).hasSize(6).matches("\\d{6}");
        verify(emailService).sendOtpEmail(eq("test@example.com"), anyString());
    }

    @Test
    void testResetPassword_Success() {
        PasswordResetToken token = PasswordResetToken.builder()
                .email("test@example.com")
                .token("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        
        when(tokenRepository.findByTokenAndEmail("123456", "test@example.com")).thenReturn(Optional.of(token));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPass")).thenReturn("newHashedPass");

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");
        request.setNewPassword("newPass");
        
        String result = authService.resetPassword(request);

        assertThat(result).contains("updated");
        assertThat(testUser.getPasswordHash()).isEqualTo("newHashedPass");
    }

    @Test
    void testHandleOAuthLogin_NewUser() {
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUserId(2L);
            return u;
        });
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong())).thenReturn("oauth-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("oauth-refresh-token");

        AuthResponse response = authService.handleOAuthLogin("oauth@example.com", "OAuth User", AuthProvider.GOOGLE);

        assertThat(response.getAccessToken()).isEqualTo("oauth-token");
        assertThat(response.getRefreshToken()).isEqualTo("oauth-refresh-token");
        verify(walletClient).initializeWallet(2L);
    }

    @Test
    void testValidateToken_Revoked() {
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(revokedTokenService.isRevoked("token")).thenReturn(true);

        boolean result = authService.validateToken("token");

        assertThat(result).isFalse();
    }

    @Test
    void testUploadProfileImage() {
        MultipartFile file = mock(MultipartFile.class);
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(testUser));
        when(fileStorageService.save(file)).thenReturn("http://image.url");

        String result = authService.uploadProfileImage(1L, file);

        assertThat(result).isEqualTo("http://image.url");
        assertThat(testUser.getProfileImage()).isEqualTo("http://image.url");
    }

    @Test
    void testUpdateUserRole() {
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(testUser));

        UserResponse response = authService.updateUserRole(1L, Role.ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateProfile_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        UserResponse response = authService.updateProfile("test@example.com", "New Name", "new-image.jpg");

        assertThat(response.getFullName()).isEqualTo("New Name");
        assertThat(testUser.getProfileImage()).isEqualTo("new-image.jpg");
    }

    @Test
    void testGetAllUsers() {
        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser));

        List<UserResponse> result = authService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void testForgotPassword_EmailNotFound() {
        when(userRepository.existsByEmail("unknown@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.forgotPassword("unknown@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
    }

    @Test
    void testResetPassword_InvalidOtp() {
        when(tokenRepository.findByTokenAndEmail("wrong-otp", "test@example.com")).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setOtp("wrong-otp");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void testResetPassword_ExpiredOtp() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .email("test@example.com")
                .token("123456")
                .expiryDate(LocalDateTime.now().minusMinutes(1)) // Already expired
                .build();

        when(tokenRepository.findByTokenAndEmail("123456", "test@example.com")).thenReturn(Optional.of(expiredToken));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        
        verify(tokenRepository).delete(expiredToken);
    }

    @Test
    void testRegister_EmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testRefreshToken_Invalid() {
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid, expired, or revoked");
    }

    @Test
    void testChangePassword_IncorrectOldPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongPass");
        request.setNewPassword("newPass");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPass", "hashedPass")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void testHandleOAuthLogin_ProviderMismatch() {
        testUser.setProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.handleOAuthLogin("test@example.com", "Name", AuthProvider.GITHUB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different login provider");
    }
    @Test
    void testLogout_BlankTokenIsIgnored() {
        authService.logout(" ");
        verify(revokedTokenService, never()).revoke(anyString(), any());
    }

    @Test
    void testLogout_RevokesValidToken() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        when(jwtUtil.extractExpiration("token")).thenReturn(expiry);

        authService.logout("token");

        verify(revokedTokenService).revoke("token", expiry);
    }

    @Test
    void testValidateToken_WhenJwtInvalid_ReturnsFalse() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        assertThat(authService.validateToken("bad-token")).isFalse();
        verify(revokedTokenService, never()).isRevoked(anyString());
    }

    @Test
    void testRefreshToken_LoadsUserIdWhenMissingInOldToken() {
        when(jwtUtil.validateToken("legacy-token")).thenReturn(true);
        when(revokedTokenService.isRevoked("legacy-token")).thenReturn(false);
        when(jwtUtil.extractUsername("legacy-token")).thenReturn("test@example.com");
        when(jwtUtil.extractRole("legacy-token")).thenReturn("USER");
        when(jwtUtil.extractUserId("legacy-token")).thenReturn(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken("test@example.com", "USER", 1L)).thenReturn("refreshed-token");
        when(jwtUtil.generateRefreshToken("test@example.com")).thenReturn("refreshed-refresh-token");

        AuthResponse response = authService.refreshToken("legacy-token");

        assertThat(response.getAccessToken()).isEqualTo("refreshed-token");
        assertThat(response.getRefreshToken()).isEqualTo("refreshed-refresh-token");
    }

    @Test
    void testChangePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPass");
        request.setNewPassword("newPass");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass", "hashedPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("encoded-new");

        String result = authService.changePassword("test@example.com", request);

        assertThat(result).isEqualTo("Password updated successfully");
        assertThat(testUser.getPasswordHash()).isEqualTo("encoded-new");
    }

    @Test
    void testGetUserProfile_NotFound() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getUserProfile(99L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void testUploadProfileImage_UserNotFound() {
        MultipartFile file = mock(MultipartFile.class);
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.uploadProfileImage(99L, file))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void testUpdateSuspendedStatus() {
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(testUser));

        UserResponse response = authService.updateSuspendedStatus(1L, true);

        assertThat(response.getSuspended()).isTrue();
        verify(userRepository).save(testUser);
    }

    @Test
    void testDeleteUser() {
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(testUser));

        authService.deleteUser(1L);

        verify(userRepository).deleteByUserId(1L);
    }

    @Test
    void testUpdateProfile_NameRequired() {
        assertThatThrownBy(() -> authService.updateProfile("test@example.com", " ", "image"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name is required");
    }
}
