package com.booknest.authservice.security;

import com.booknest.authservice.enums.AuthProvider;
import com.booknest.authservice.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Custom success handler for OAuth2 (Google/GitHub) authentication.
 * Orchestrates user synchronization and redirects the user back to the frontend with a JWT.
 */
@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${OAUTH2_REDIRECT_URL:http://localhost:4200/oauth2/redirect}")
    private String redirectUrl;

    // Processes successful social logins, extracts user details, and redirects to the frontend with a generated token
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oAuth2User = token.getPrincipal();
            
            String registrationId = token.getAuthorizedClientRegistrationId();
            AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
            
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            
            if (provider == AuthProvider.GITHUB && email == null) {
                // Fallback for GitHub if email is private
                email = oAuth2User.getAttribute("login") + "@github.com";
            }
            
            if (name == null) {
                name = oAuth2User.getAttribute("login");
            }
            
            if (email == null) {
                throw new IllegalArgumentException("Email not provided by " + provider);
            }

            com.booknest.authservice.dto.AuthResponse authResponse = authService.handleOAuthLogin(email, name, provider);
            
            // Set refresh token cookie
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refreshToken", authResponse.getRefreshToken());
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            response.addCookie(cookie);
            
            String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                    .queryParam("token", authResponse.getAccessToken())
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } catch (Exception e) {
            log.error("OAuth2 Authentication Success Handler Error: ", e);
            
            // Redirect to frontend with error message instead of showing 500 Whitelabel page
            String errorUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                    .queryParam("error", e.getMessage())
                    .build().toUriString();
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }
}
