package com.booknest.walletservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDoFilterInternal_ValidToken() throws ServletException, IOException {
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(jwtUtil.extractRole(token)).thenReturn("ADMIN");
        when(jwtUtil.extractUserId(token)).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, times(1)).validateToken(token);
        verify(request, times(1)).setAttribute("authenticatedSubject", "testuser");
        verify(request, times(1)).setAttribute("authenticatedRole", "ADMIN");
        verify(request, times(1)).setAttribute("authenticatedUserId", 1L);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ValidTokenWithRolePrefix() throws ServletException, IOException {
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(jwtUtil.extractRole(token)).thenReturn("ROLE_ADMIN");
        when(jwtUtil.extractUserId(token)).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, times(1)).validateToken(token);
        verify(request, times(1)).setAttribute("authenticatedSubject", "testuser");
        verify(request, times(1)).setAttribute("authenticatedRole", "ADMIN");
        verify(request, times(1)).setAttribute("authenticatedUserId", 1L);
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_ValidTokenNullRole() throws ServletException, IOException {
        String token = "valid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUsername(token)).thenReturn("testuser");
        when(jwtUtil.extractRole(token)).thenReturn(null);
        when(jwtUtil.extractUserId(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, times(1)).validateToken(token);
        verify(request, times(1)).setAttribute("authenticatedSubject", "testuser");
        verify(request, times(1)).setAttribute("authenticatedRole", "USER");
        verify(request, never()).setAttribute(eq("authenticatedUserId"), any());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_InvalidToken() throws ServletException, IOException {
        String token = "invalid.token.here";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, times(1)).validateToken(token);
        verify(request, never()).setAttribute(anyString(), any());
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilterInternal_NoHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testDoFilterInternal_InvalidHeader() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcm5hbWU6cGFzc3dvcmQ=");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil, never()).validateToken(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
