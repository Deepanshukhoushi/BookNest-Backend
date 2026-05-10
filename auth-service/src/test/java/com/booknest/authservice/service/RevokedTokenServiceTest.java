package com.booknest.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class RevokedTokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RevokedTokenService revokedTokenService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        revokedTokenService = new RevokedTokenService(redisTemplate);
    }

    @Test
    void revokeAndIsRevoked_HandleNullAndBlank() {
        revokedTokenService.revoke(null, null);
        revokedTokenService.revoke(" ", null);

        assertThat(revokedTokenService.isRevoked(null)).isFalse();
        assertThat(revokedTokenService.isRevoked(" ")).isFalse();

        verifyNoInteractions(valueOperations);
    }

    @Test
    void revoke_ShouldStoreInRedisWhenTtlIsPositive() {
        String token = "active-token";
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        
        revokedTokenService.revoke(token, expiry);
        
        verify(valueOperations).set(startsWith("revoked:"), eq("1"), any());
    }

    @Test
    void revoke_ShouldNotStoreInRedisWhenTtlIsNegative() {
        String token = "expired-token";
        Date expiry = new Date(System.currentTimeMillis() - 10_000);
        
        revokedTokenService.revoke(token, expiry);
        
        verifyNoInteractions(valueOperations);
    }

    @Test
    void isRevoked_ShouldReturnRedisResult() {
        String token = "some-token";
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        
        assertThat(revokedTokenService.isRevoked(token)).isTrue();
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        assertThat(revokedTokenService.isRevoked(token)).isFalse();
    }
}
