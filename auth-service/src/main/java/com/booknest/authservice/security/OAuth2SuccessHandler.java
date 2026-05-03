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
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${OAUTH2_REDIRECT_URL:http://localhost:4200/oauth2/redirect}")
    private String redirectUrl;

    // Processes successful social logins, extracts user details, and redirects to the frontend with a generated token
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = token.getPrincipal();
        
        String registrationId = token.getAuthorizedClientRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        
        if (provider == AuthProvider.GITHUB && email == null) {
            // Fallback for GitHub if email is private (might need additional API call in production)
            email = oAuth2User.getAttribute("login") + "@github.com";
        }
        
        if (name == null) {
            name = oAuth2User.getAttribute("login");
        }
        
        String jwtToken = authService.handleOAuthLogin(email, name, provider);
        
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                .queryParam("token", jwtToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
