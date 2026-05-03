package com.booknest.walletservice.repository;

import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.TransactionType;
import com.booknest.walletservice.entity.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatementsRepositoryTest {

    @Autowired
    private StatementsRepository statementsRepository;

    @Autowired
    private WalletRepository walletRepository;

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
                .userId(1L)
                .currentBalance(500.0)
                .build();
        testWallet = walletRepository.save(testWallet);
    }

    @Test
    void testFindByWallet_WalletId() {
        Statement s1 = Statement.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(100.0)
                .wallet(testWallet)
                .dateTime(LocalDateTime.now())
                .build();
        statementsRepository.save(s1);

        List<Statement> results = statementsRepository.findByWallet_WalletId(testWallet.getWalletId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAmount()).isEqualTo(100.0);
    }

    @Test
    void testFindByTransactionType() {
        Statement s1 = Statement.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(100.0)
                .wallet(testWallet)
                .dateTime(LocalDateTime.now())
                .build();
        Statement s2 = Statement.builder()
                .transactionType(TransactionType.WITHDRAW)
                .amount(50.0)
                .wallet(testWallet)
                .dateTime(LocalDateTime.now())
                .build();
        statementsRepository.save(s1);
        statementsRepository.save(s2);

        List<Statement> deposits = statementsRepository.findByTransactionType(TransactionType.DEPOSIT);
        assertThat(deposits).hasSize(1);
        assertThat(deposits.get(0).getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void testFindByOrderId() {
        Statement s1 = Statement.builder()
                .transactionType(TransactionType.WITHDRAW)
                .amount(200.0)
                .orderId(101L)
                .wallet(testWallet)
                .dateTime(LocalDateTime.now())
                .build();
        statementsRepository.save(s1);

        List<Statement> results = statementsRepository.findByOrderId(101L);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrderId()).isEqualTo(101L);
    }
}
