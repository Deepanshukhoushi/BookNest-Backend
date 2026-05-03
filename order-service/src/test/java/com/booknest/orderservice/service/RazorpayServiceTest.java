package com.booknest.orderservice.service;

import com.booknest.orderservice.dto.PaymentVerifyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpayServiceTest {

    @Test
    void simulationMode_UsesSimulatedValues() throws Exception {
        RazorpayService service = new RazorpayService();
        PaymentVerifyRequest valid = PaymentVerifyRequest.builder()
                .orderId("sim_order_123")
                .paymentId("sim_payment_456")
                .signature(RazorpayService.SIMULATED_SIGNATURE)
                .build();
        PaymentVerifyRequest invalid = PaymentVerifyRequest.builder()
                .orderId("real_order")
                .paymentId("sim_payment_456")
                .signature("wrong")
                .build();

        assertThat(service.isSimulationMode()).isTrue();
        assertThat(service.getPublicKey()).isEqualTo(RazorpayService.SIMULATED_PUBLIC_KEY);
        assertThat(service.createOrder(123.45)).startsWith("sim_order_");
        assertThat(service.verifySignature(valid)).isTrue();
        assertThat(service.verifySignature(invalid)).isFalse();
        assertThat(service.verifySignature(null)).isFalse();
        assertThat(service.verifyWebhookSignature("{}", "sig")).isFalse();
    }

    @Test
    void configuredMode_HandlesVerificationFailuresGracefully() {
        RazorpayService service = new RazorpayService();
        ReflectionTestUtils.setField(service, "keyId", "rzp_test");
        ReflectionTestUtils.setField(service, "keySecret", "secret");
        ReflectionTestUtils.setField(service, "webhookSecret", "webhook");

        assertThat(service.isSimulationMode()).isFalse();
        assertThat(service.getPublicKey()).isEqualTo("rzp_test");
        assertThat(service.verifySignature(null)).isFalse();
        assertThat(service.verifyWebhookSignature("{}", "sig")).isFalse();
    }

    @Test
    void blankSecretStillBehavesAsSimulationMode() {
        RazorpayService service = new RazorpayService();
        ReflectionTestUtils.setField(service, "keyId", "configured");
        ReflectionTestUtils.setField(service, "keySecret", "");

        assertThat(service.isSimulationMode()).isTrue();
        assertThat(service.getPublicKey()).isEqualTo(RazorpayService.SIMULATED_PUBLIC_KEY);
    }
}
