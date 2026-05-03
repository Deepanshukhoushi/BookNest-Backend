package com.booknest.walletservice.service;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RazorpayServiceTest {

    private RazorpayService razorpayService;

    @BeforeEach
    void setUp() {
        razorpayService = new RazorpayService();
    }

    @Test
    void isSimulationMode_trueWhenKeysAreNull() {
        assertThat(razorpayService.isSimulationMode()).isTrue();
    }

    @Test
    void isSimulationMode_falseWhenKeysArePresent() {
        ReflectionTestUtils.setField(razorpayService, "keyId", "test_id");
        ReflectionTestUtils.setField(razorpayService, "keySecret", "test_secret");
        
        assertThat(razorpayService.isSimulationMode()).isFalse();
    }

    @Test
    void getPublicKey_simulationMode() {
        assertThat(razorpayService.getPublicKey()).isEqualTo(RazorpayService.SIMULATED_PUBLIC_KEY);
    }

    @Test
    void getPublicKey_realMode() {
        ReflectionTestUtils.setField(razorpayService, "keyId", "real_key");
        ReflectionTestUtils.setField(razorpayService, "keySecret", "test_secret");

        assertThat(razorpayService.getPublicKey()).isEqualTo("real_key");
    }

    @Test
    void createOrder_simulationMode() throws RazorpayException {
        String orderId = razorpayService.createOrder(100.0);
        assertThat(orderId).startsWith("sim_order_");
    }

    @Test
    void createOrder_realMode_noCredentials_throwsException() {
        ReflectionTestUtils.setField(razorpayService, "keyId", "test_id");
        // secret is still null, so validateRazorpayCredentials() throws IllegalStateException
        assertThatThrownBy(() -> razorpayService.createOrder(100.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Razorpay credentials are not configured");
    }

    @Test
    void verifySignature_simulationMode_valid() {
        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setOrderId("sim_order_123");
        req.setPaymentId("sim_payment_123");
        req.setSignature(RazorpayService.SIMULATED_SIGNATURE);

        assertThat(razorpayService.verifySignature(req)).isTrue();
    }

    @Test
    void verifySignature_simulationMode_invalidOrderId() {
        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setOrderId("real_order_123");
        req.setPaymentId("sim_payment_123");
        req.setSignature(RazorpayService.SIMULATED_SIGNATURE);

        assertThat(razorpayService.verifySignature(req)).isFalse();
    }

    @Test
    void verifySignature_simulationMode_nullRequest() {
        assertThat(razorpayService.verifySignature(null)).isFalse();
    }

    @Test
    void fetchOrderAmount_simulationMode() throws RazorpayException {
        assertThat(razorpayService.fetchOrderAmount("sim_order_123")).isEqualTo(0.0);
    }
}
