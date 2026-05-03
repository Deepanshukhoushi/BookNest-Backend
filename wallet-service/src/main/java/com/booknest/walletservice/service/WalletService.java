package com.booknest.walletservice.service;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.booknest.walletservice.dto.WalletRequest;
import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.Wallet;

import java.util.List;

public interface WalletService {
    List<Wallet> getWallets();
    Wallet addWallet(Wallet wallet);
    Wallet addMoney(Long walletId, Double amount, String paymentGateway);
    Wallet payMoney(Long walletId, Double amount, Long orderId);
    Wallet getById(Long walletId);
    List<Statement> getStatementsByWalletId(Long walletId);
    List<Statement> getAllStatements();
    void deleteById(Long walletId);
    Wallet getByUserId(Long userId);

    Wallet initializeWallet(Long userId);

    String initiateRazorpayTopUp(WalletRequest request);

    Wallet verifyRazorpayTopUp(PaymentVerifyRequest request, Long userId);

    String getRazorpayPublicKey();
}
