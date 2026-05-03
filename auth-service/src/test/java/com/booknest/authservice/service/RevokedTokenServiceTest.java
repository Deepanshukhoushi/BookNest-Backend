package com.booknest.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RevokedTokenServiceTest {

    private final RevokedTokenService revokedTokenService = new RevokedTokenService();

    @Test
    void revokeAndIsRevoked_HandleNullBlankAndExpiryCleanup() {
        revokedTokenService.revoke(null, null);
        revokedTokenService.revoke(" ", null);

        assertThat(revokedTokenService.isRevoked(null)).isFalse();
        assertThat(revokedTokenService.isRevoked(" ")).isFalse();

        String activeToken = "active-token";
        revokedTokenService.revoke(activeToken, new Date(System.currentTimeMillis() + 60_000));
        assertThat(revokedTokenService.isRevoked(activeToken)).isTrue();

        String expiredToken = "expired-token";
        revokedTokenService.revoke(expiredToken, new Date(System.currentTimeMillis() - 1_000));
        assertThat(revokedTokenService.isRevoked(expiredToken)).isFalse();
    }
}
