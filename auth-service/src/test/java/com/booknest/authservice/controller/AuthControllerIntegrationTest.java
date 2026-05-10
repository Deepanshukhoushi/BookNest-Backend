package com.booknest.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.booknest.authservice.service.AuthService;
import com.booknest.authservice.dto.AuthResponse;
import com.booknest.authservice.dto.UserResponse;
import com.booknest.authservice.enums.Role;

@ExtendWith(MockitoExtension.class)
class AuthControllerIntegrationTest {

        private MockMvc mockMvc;

        @Mock
        private AuthService authService;

        @InjectMocks
        private AuthController authController;

        @BeforeEach
        void setup() {
                LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
                validator.afterPropertiesSet();
                mockMvc = MockMvcBuilders.standaloneSetup(authController)
                                .setValidator(validator)
                                .build();
        }

        @Test
        void register_validPayload_returns200AndMessage() throws Exception {
                when(authService.register(any())).thenReturn("User registered successfully");

                String payload = """
                                {
                                  "fullName": "Alice Johnson",
                                  "email": "alice@example.com",
                                  "password": "Password@123",
                                  "mobile": "9876543210"
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("User registered successfully"))
                                .andExpect(jsonPath("$.data").value("User registered successfully"));
        }

        @Test
        void register_invalidEmailFormat_returns400() throws Exception {
                String payload = """
                                {
                                  "fullName": "Alice Johnson",
                                  "email": "invalid-email",
                                  "password": "Password@123",
                                  "mobile": "9876543210"
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void login_successfulRequest_returns200AndJwtToken() throws Exception {
                AuthResponse authResponse = AuthResponse.builder()
                                .accessToken("jwt-token")
                                .refreshToken("refresh-token")
                                .build();
                when(authService.login(any())).thenReturn(authResponse);

                String payload = """
                                {
                                  "email": "bob@example.com",
                                  "password": "Password@123"
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("Login successful"))
                                .andExpect(jsonPath("$.data").value("jwt-token"));
        }

        @Test
        void login_emptyPassword_returns400() throws Exception {
                String payload = """
                                {
                                  "email": "bob@example.com",
                                  "password": ""
                                }
                                """;

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refresh_validTokenParam_returns200AndRefreshedToken() throws Exception {
                AuthResponse authResponse = AuthResponse.builder()
                                .accessToken("new-token")
                                .refreshToken("new-refresh-token")
                                .build();
                when(authService.refreshToken(eq("old-token"))).thenReturn(authResponse);

                mockMvc.perform(post("/api/v1/auth/refresh").param("token", "old-token"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                                .andExpect(jsonPath("$.data").value("new-token"));

                verify(authService).refreshToken("old-token");
        }

        @Test
        void refresh_missingTokenParam_returns401() throws Exception {
                mockMvc.perform(post("/api/v1/auth/refresh"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void profile_validUserId_returns200() throws Exception {
                when(authService.getUserProfile(1L)).thenReturn(UserResponse.builder()
                                .userId(1L)
                                .fullName("Alice Johnson")
                                .email("alice@example.com")
                                .role(Role.USER)
                                .build());

                mockMvc.perform(get("/api/v1/auth/profile/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.userId").value(1))
                                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                                .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        void forgotPassword_validEmail_returns200() throws Exception {
                when(authService.forgotPassword("test@example.com")).thenReturn("OTP Sent");

                mockMvc.perform(post("/api/v1/auth/forgot-password")
                                .param("email", "test@example.com"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Verification process initiated"));
        }

        @Test
        void resetPassword_validRequest_returns200() throws Exception {
                when(authService.resetPassword(any())).thenReturn("Success");

                String payload = "{\"email\":\"test@example.com\", \"otp\":\"123456\", \"newPassword\":\"Pass@123\"}";

                mockMvc.perform(post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Security reset successful"));
        }

        @Test
        void validateToken_validToken_returns200() throws Exception {
                when(authService.validateToken("valid-token")).thenReturn(true);

                mockMvc.perform(get("/api/v1/auth/validate").param("token", "valid-token"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        void validateToken_invalidToken_returns401() throws Exception {
                when(authService.validateToken("invalid-token")).thenReturn(false);

                mockMvc.perform(get("/api/v1/auth/validate").param("token", "invalid-token"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void logout_withBearerToken_returns200() throws Exception {
                mockMvc.perform(post("/api/v1/auth/logout")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-token"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Logout successful"));

                verify(authService).logout("jwt-token");
        }

        @Test
        void logout_missingBearerToken_returns401() throws Exception {
                mockMvc.perform(post("/api/v1/auth/logout"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getProfile_forbiddenWhenRequestTargetsDifferentUser() throws Exception {
                mockMvc.perform(get("/api/v1/auth/profile/2")
                                .header("X-Auth-UserId", "1")
                                .header("X-Auth-Role", "USER"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void getUserById_acceptsAdminRolePrefix() throws Exception {
                when(authService.getUserProfile(2L)).thenReturn(UserResponse.builder()
                                .userId(2L)
                                .email("user2@example.com")
                                .role(Role.USER)
                                .build());

                mockMvc.perform(get("/api/v1/auth/user/2")
                                .header("X-Auth-UserId", "1")
                                .header("X-Auth-Role", "ROLE_ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.userId").value(2));
        }

        @Test
        void getProfile_invalidAuthenticatedUserHeader_returns401() throws Exception {
                mockMvc.perform(get("/api/v1/auth/profile/1")
                                .header("X-Auth-UserId", "bad")
                                .header("X-Auth-Role", "USER"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void changePassword_usesAuthenticatedPrincipal() throws Exception {
                when(authService.changePassword(eq("alice@example.com"), any()))
                                .thenReturn("Password updated successfully");

                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken("alice@example.com", "n/a"));

                try {
                        String payload = """
                                        {
                                          "oldPassword": "OldPass@123",
                                          "newPassword": "NewPass@123"
                                        }
                                        """;

                        mockMvc.perform(post("/api/v1/auth/change-password")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.message").value("Password changed successfully"));
                } finally {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
        }

        @Test
        void getAllUsers_returns200() throws Exception {
                when(authService.getAllUsers())
                                .thenReturn(java.util.List.of(UserResponse.builder().userId(1L).build()));

                mockMvc.perform(get("/api/v1/auth/all"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].userId").value(1));
        }

        @Test
        void updateUserRole_returns200() throws Exception {
                when(authService.updateUserRole(eq(1L), eq(Role.ADMIN))).thenReturn(UserResponse.builder()
                                .userId(1L)
                                .role(Role.ADMIN)
                                .build());

                mockMvc.perform(put("/api/v1/auth/users/1/role")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }

        @Test
        void suspendReactivateAndDeleteUser_return200() throws Exception {
                when(authService.updateSuspendedStatus(1L, true))
                                .thenReturn(UserResponse.builder().userId(1L).suspended(true).build());
                when(authService.updateSuspendedStatus(1L, false))
                                .thenReturn(UserResponse.builder().userId(1L).suspended(false).build());

                mockMvc.perform(put("/api/v1/auth/users/1/suspend"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.suspended").value(true));

                mockMvc.perform(put("/api/v1/auth/users/1/reactivate"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.suspended").value(false));

                mockMvc.perform(delete("/api/v1/auth/users/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("User deleted successfully"));
        }
}
