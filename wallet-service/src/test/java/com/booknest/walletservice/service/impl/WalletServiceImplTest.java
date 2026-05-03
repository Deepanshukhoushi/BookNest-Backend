package com.booknest.walletservice.service.impl;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.booknest.walletservice.dto.WalletRequest;
import com.booknest.walletservice.entity.Statement;
import com.booknest.walletservice.entity.Wallet;
import com.booknest.walletservice.exception.InsufficientBalanceException;
import com.booknest.walletservice.exception.InvalidPaymentException;
import com.booknest.walletservice.repository.StatementsRepository;
import com.booknest.walletservice.repository.WalletRepository;
import com.booknest.walletservice.service.RazorpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private StatementsRepository statementsRepository;

    @Mock
    private RazorpayService razorpayService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet testWallet;
    private final Long userId = 1L;
    private final Long walletId = 100L;

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
                .walletId(walletId)
                .userId(userId)
                .currentBalance(500.0)
                .build();
    }

    @Test
    void testGetWallets() {
        when(walletRepository.findAll()).thenReturn(Arrays.asList(testWallet));
        List<Wallet> result = walletService.getWallets();
        assertThat(result).hasSize(1);
    }

    @Test
    void testAddWallet() {
        when(walletRepository.save(any())).thenReturn(testWallet);
        Wallet result = walletService.addWallet(new Wallet());
        assertThat(result.getCurrentBalance()).isEqualTo(500.0);
    }

    @Test
    void testAddMoney_Success() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.addMoney(walletId, 200.0, "razorpay");

        assertThat(result.getCurrentBalance()).isEqualTo(700.0);
        verify(statementsRepository).save(any(Statement.class));
    }

    @Test
    void testAddMoney_InvalidAmount() {
        assertThatThrownBy(() -> walletService.addMoney(walletId, -10.0, "test"))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void testPayMoney_Success() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.payMoney(walletId, 100.0, 1L);

        assertThat(result.getCurrentBalance()).isEqualTo(400.0);
        verify(statementsRepository).save(any(Statement.class));
    }

    @Test
    void testPayMoney_InsufficientBalance() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));

        assertThatThrownBy(() -> walletService.payMoney(walletId, 1000.0, 1L))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void testGetByUserId_Found() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(testWallet));
        Wallet result = walletService.getByUserId(userId);
        assertThat(result.getWalletId()).isEqualTo(walletId);
    }

    @Test
    void testInitializeWallet_Exists() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(testWallet));
        Wallet result = walletService.initializeWallet(userId);
        verify(walletRepository, never()).save(any());
        assertThat(result).isEqualTo(testWallet);
    }

    @Test
    void testInitializeWallet_New() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.initializeWallet(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getCurrentBalance()).isEqualTo(0.0);
    }

    @Test
    void testVerifyRazorpayTopUp_Success() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);

        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(true);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.verifyRazorpayTopUp(request, userId);

        assertThat(result.getCurrentBalance()).isEqualTo(700.0);
    }

    @Test
    void testVerifyRazorpayTopUp_InvalidSignature() {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);
        when(razorpayService.verifySignature(any())).thenReturn(false);

        assertThatThrownBy(() -> walletService.verifyRazorpayTopUp(request, userId))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void testGetById_NotFound() {
        when(walletRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> walletService.getById(walletId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void testAddMoney_WalletNotFound() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> walletService.addMoney(walletId, 100.0, "test"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testPayMoney_WalletNotFound() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> walletService.payMoney(walletId, 100.0, 1L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testVerifyRazorpayTopUp_WalletNotFound() {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(true);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.verifyRazorpayTopUp(request, userId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testGetStatementsByWalletId() {
        Statement statement = Statement.builder().amount(100.0).build();
        when(statementsRepository.findByWallet_WalletId(walletId)).thenReturn(Arrays.asList(statement));
        List<Statement> result = walletService.getStatementsByWalletId(walletId);
        assertThat(result).hasSize(1);
    }

    @Test
    void testGetAllStatements() {
        Statement statement = Statement.builder().amount(100.0).build();
        when(statementsRepository.findAll()).thenReturn(Arrays.asList(statement));
        List<Statement> result = walletService.getAllStatements();
        assertThat(result).hasSize(1);
    }

    @Test
    void testDeleteById() {
        doNothing().when(walletRepository).deleteById(walletId);
        walletService.deleteById(walletId);
        verify(walletRepository).deleteById(walletId);
    }

    @Test
    void testInitiateRazorpayTopUp() throws Exception {
        when(razorpayService.createOrder(200.0)).thenReturn("rzp_order_1");
        String result = walletService.initiateRazorpayTopUp(new WalletRequest(userId, walletId, 200.0, null, "razorpay"));
        assertThat(result).isEqualTo("rzp_order_1");
    }

    @Test
    void testGetRazorpayPublicKey() {
        when(razorpayService.getPublicKey()).thenReturn("pk_test");
        String result = walletService.getRazorpayPublicKey();
        assertThat(result).isEqualTo("pk_test");
    }

    @Test
    void testAddWallet_DefaultBalance() {
        Wallet wallet = new Wallet();
        wallet.setCurrentBalance(null);
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        
        Wallet result = walletService.addWallet(wallet);
        assertThat(result.getCurrentBalance()).isEqualTo(0.0);
    }

    @Test
    void testPayMoney_InvalidAmount() {
        assertThatThrownBy(() -> walletService.payMoney(walletId, 0.0, 1L))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> walletService.payMoney(walletId, -50.0, 1L))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void testVerifyRazorpayTopUp_NonSimulationMode() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(false);
        when(razorpayService.fetchOrderAmount("order_1")).thenReturn(200.0);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.verifyRazorpayTopUp(request, userId);
        assertThat(result.getCurrentBalance()).isEqualTo(700.0);
    }

    @Test
    void testVerifyRazorpayTopUp_AmountMismatch() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(false);
        when(razorpayService.fetchOrderAmount("order_1")).thenReturn(300.0); // Different from request

        assertThatThrownBy(() -> walletService.verifyRazorpayTopUp(request, userId))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("Amount mismatch");
    }

    @Test
    void testVerifyRazorpayTopUp_GenericException() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest("order_1", "pay_1", "sig_1", 200.0);
        when(razorpayService.verifySignature(any())).thenReturn(true);
        when(razorpayService.isSimulationMode()).thenReturn(true);
        when(walletRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> walletService.verifyRazorpayTopUp(request, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment verification failed");
    }

    @Test
    void testInitiateRazorpayTopUp_Failure() throws Exception {
        when(razorpayService.createOrder(anyDouble())).thenThrow(new com.razorpay.RazorpayException("API Error"));
        
        assertThatThrownBy(() -> walletService.initiateRazorpayTopUp(new WalletRequest(userId, walletId, 200.0, null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment initiation failed");
    }

    // ── Additional branch coverage ────────────────────────────────────────────

    @Test
    void testGetByUserId_NotFound() {
        when(walletRepository.findByUserId(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> walletService.getByUserId(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Wallet not found for userId");
    }

    @Test
    void testAddMoney_NullAmount() {
        assertThatThrownBy(() -> walletService.addMoney(walletId, null, "test"))
                .isInstanceOf(com.booknest.walletservice.exception.InvalidPaymentException.class)
                .hasMessageContaining("greater than 0");
    }

    @Test
    void testAddMoney_NullPaymentGateway_NoViaInRemark() {
        when(walletRepository.findAndLockByWalletId(walletId)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wallet result = walletService.addMoney(walletId, 50.0, null);

        assertThat(result.getCurrentBalance()).isEqualTo(550.0);
        // statement remark should NOT contain "via"
        verify(statementsRepository).save(argThat(s ->
                !s.getTransactionRemarks().contains("via")));
    }
}

