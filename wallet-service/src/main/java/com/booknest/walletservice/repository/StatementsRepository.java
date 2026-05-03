package com.booknest.walletservice.repository;

import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementsRepository extends JpaRepository<Statement, Long> {
    List<Statement> findByWallet_WalletId(Long walletId);
    List<Statement> findByTransactionType(TransactionType type);
    List<Statement> findByOrderId(Long orderId);
}
