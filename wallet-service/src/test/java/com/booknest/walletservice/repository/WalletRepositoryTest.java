package com.booknest.walletservice.repository;

import com.booknest.walletservice.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WalletRepositoryTest {

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void testSaveAndFindByUserId() {
        Wallet wallet = Wallet.builder()
                .userId(1L)
                .currentBalance(100.0)
                .build();
        walletRepository.save(wallet);

        Optional<Wallet> found = walletRepository.findByUserId(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getCurrentBalance()).isEqualTo(100.0);
    }

    @Test
    void testFindAndLockByWalletId() {
        Wallet wallet = Wallet.builder()
                .userId(2L)
                .currentBalance(200.0)
                .build();
        Wallet saved = walletRepository.save(wallet);

        Optional<Wallet> found = walletRepository.findAndLockByWalletId(saved.getWalletId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(2L);
    }

    // ── Additional repository coverage ────────────────────────────────────────

    @Test
    void testFindByUserId_NotFound() {
        Optional<Wallet> found = walletRepository.findByUserId(9999L);
        assertThat(found).isEmpty();
    }

    @Test
    void testFindAndLockByWalletId_NotFound() {
        Optional<Wallet> found = walletRepository.findAndLockByWalletId(9999L);
        assertThat(found).isEmpty();
    }

    @Test
    void testDeleteById() {
        Wallet wallet = Wallet.builder().userId(5L).currentBalance(0.0).build();
        Wallet saved = walletRepository.save(wallet);

        walletRepository.deleteById(saved.getWalletId());

        assertThat(walletRepository.findById(saved.getWalletId())).isEmpty();
    }

    @Test
    void testFindAll_returnsAllWallets() {
        walletRepository.save(Wallet.builder().userId(11L).currentBalance(10.0).build());
        walletRepository.save(Wallet.builder().userId(12L).currentBalance(20.0).build());

        assertThat(walletRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}

