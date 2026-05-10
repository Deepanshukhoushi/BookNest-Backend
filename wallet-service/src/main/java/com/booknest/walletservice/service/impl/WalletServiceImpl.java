package com.booknest.walletservice.service.impl;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.booknest.walletservice.dto.WalletRequest;
import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.TransactionType;
import com.booknest.walletservice.entity.Wallet;
import com.booknest.walletservice.exception.InsufficientBalanceException;
import com.booknest.walletservice.exception.InvalidPaymentException;
import com.booknest.walletservice.repository.StatementsRepository;
import com.booknest.walletservice.repository.WalletRepository;
import com.booknest.walletservice.service.RazorpayService;
import com.booknest.walletservice.service.WalletService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service implementation for managing digital wallets, balances, and transactions.
 * Handles depositing funds, paying for orders, and tracking transaction history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final StatementsRepository statementsRepository;
    private final RazorpayService razorpayService;

    // Retrieves a list of all digital wallets in the system
    @Override
    public List<Wallet> getWallets() {
        return walletRepository.findAll();
    }

    // Creates and persists a new wallet record
    @Override
    @Transactional
    public Wallet addWallet(Wallet wallet) {
        if (wallet.getCurrentBalance() == null) {
            wallet.setCurrentBalance(0.0);
        }
        return walletRepository.save(wallet);
    }

    // Increases the wallet balance and logs the deposit transaction
    @Override
    @Transactional
    public Wallet addMoney(Long walletId, Double amount, String paymentGateway) {
        if (amount == null || amount <= 0) {
            throw new InvalidPaymentException("Top-up amount must be greater than 0");
        }
        Wallet wallet = walletRepository.findAndLockByWalletId(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        wallet.setCurrentBalance(wallet.getCurrentBalance() + amount);

        Statement statement = Statement.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(amount)
                .dateTime(LocalDateTime.now())
                .transactionRemarks("Money deposited" + (paymentGateway != null ? " via " + paymentGateway.toUpperCase() : ""))
                .paymentGateway(paymentGateway)
                .wallet(wallet)
                .build();

        statementsRepository.save(statement);
        return walletRepository.save(wallet);
    }

    // Deducts funds from the wallet for a purchase and logs the withdrawal
    @Override
    @Transactional
    public Wallet payMoney(Long walletId, Double amount, Long orderId) {
        if (amount == null || amount <= 0) {
            throw new InvalidPaymentException("Payment amount must be greater than 0");
        }
        Wallet wallet = walletRepository.findAndLockByWalletId(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getCurrentBalance() < amount) {
            throw new InsufficientBalanceException("Insufficient balance in wallet");
        }

        wallet.setCurrentBalance(wallet.getCurrentBalance() - amount);

        Statement statement = Statement.builder()
                .transactionType(TransactionType.WITHDRAW)
                .amount(amount)
                .dateTime(LocalDateTime.now())
                .orderId(orderId)
                .transactionRemarks("Payment for Order ID: " + orderId)
                .wallet(wallet)
                .build();

        statementsRepository.save(statement);
        return walletRepository.save(wallet);
    }

    // Retrieves a wallet by its ID
    @Override
    public Wallet getById(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }

    // Fetches all transaction statements for a given wallet
    @Override
    public List<Statement> getStatementsByWalletId(Long walletId) {
        return statementsRepository.findByWallet_WalletId(walletId);
    }

    // Retrieves a list of all transaction statements in the system
    @Override
    public List<Statement> getAllStatements() {
        return statementsRepository.findAll();
    }

    // Removes a wallet from the database by its ID
    @Override
    @Transactional
    public void deleteById(Long walletId) {
        walletRepository.deleteById(walletId);
    }

    // Retrieves the wallet belonging to a specific user
    @Override
    public Wallet getByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + userId));
    }

    @Override
    @Transactional
    public Wallet addMoneyToWallet(Long userId, Double amount) {
        Wallet wallet = getByUserId(userId);
        return addMoney(wallet.getWalletId(), amount, "SYSTEM_REFUND");
    }

    // Initializes a wallet for a user if one doesn't already exist
    @Override
    @Transactional
    public Wallet initializeWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Initializing new wallet for userId: {}", userId);
                    Wallet newWallet = Wallet.builder()
                            .userId(userId)
                            .currentBalance(0.0)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    @Override
    public String initiateRazorpayTopUp(WalletRequest request) {
        log.info("Initiating Razorpay wallet top-up for userId: {}, amount: {}", request.getUserId(), request.getAmount());
        try {
            return razorpayService.createOrder(request.getAmount());
        } catch (RazorpayException e) {
            log.error("Failed to initiate Razorpay top-up: {}", e.getMessage());
            throw new RuntimeException("Payment initiation failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Wallet verifyRazorpayTopUp(PaymentVerifyRequest request, Long userId) {
        log.info("Verifying Razorpay wallet top-up for userId: {}, orderId: {}", userId, request.getOrderId());
        
        boolean isValid = razorpayService.verifySignature(request);
        if (!isValid) {
            log.error("Invalid Razorpay signature for orderId: {}", request.getOrderId());
            throw new InvalidPaymentException("Payment verification failed: Invalid signature");
        }

        // Fetch amount from Razorpay if not in simulation mode
        double amount;
        try {
            if (razorpayService.isSimulationMode()) {
                amount = request.getAmount() != null ? request.getAmount() : 0.0;
            } else {
                // Real implementation: Fetch order from Razorpay to get the amount
                // This is a secure way to ensure the amount hasn't been tampered with
                amount = razorpayService.fetchOrderAmount(request.getOrderId());
                
                // Optional: validate that the fetched amount matches the one requested by frontend
                if (request.getAmount() != null && Math.abs(amount - request.getAmount()) > 0.01) {
                    log.error("Amount mismatch: Razorpay={}, Request={}", amount, request.getAmount());
                    throw new InvalidPaymentException("Payment verification failed: Amount mismatch");
                }
            }

            Wallet wallet = getByUserId(userId);
            return addMoney(wallet.getWalletId(), amount, "razorpay");
        } catch (InvalidPaymentException e) {
            log.error("Payment validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Razorpay payment: {}", e.getMessage());
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }
    }

    @Override
    public String getRazorpayPublicKey() {
        return razorpayService.getPublicKey();
    }
}
