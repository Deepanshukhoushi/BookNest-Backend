package com.booknest.walletservice.service;

import com.booknest.walletservice.dto.PaymentVerifyRequest;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with the Razorpay payment gateway.
 * Handles the creation of transaction orders and the verification of secure payment signatures.
 */
@Service
public class RazorpayService {

    public static final String SIMULATED_PUBLIC_KEY = "sim_public_key";
    public static final String SIMULATED_SIGNATURE = "sim_signature";
    private static final String SIMULATED_ORDER_PREFIX = "sim_order_";
    private static final String SIMULATED_PAYMENT_PREFIX = "sim_payment_";

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    public String getPublicKey() {
        if (isSimulationMode()) {
            return SIMULATED_PUBLIC_KEY;
        }
        return keyId;
    }

    // Creates a new transaction order in the Razorpay system
    public String createOrder(double amount) throws RazorpayException {
        if (isSimulationMode()) {
            return SIMULATED_ORDER_PREFIX + System.currentTimeMillis();
        }
        validateRazorpayCredentials();
        RazorpayClient client = new RazorpayClient(keyId, keySecret);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", (int)(amount * 100)); // Amount in paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "txn_wallet_" + System.currentTimeMillis());

        Order order = client.orders.create(orderRequest);
        return order.get("id");
    }

    // Verifies that the payment signature provided by Razorpay is authentic
    public boolean verifySignature(PaymentVerifyRequest request) {
        try {
            if (isSimulationMode()) {
                return request != null
                        && request.getOrderId() != null
                        && request.getOrderId().startsWith(SIMULATED_ORDER_PREFIX)
                        && request.getPaymentId() != null
                        && request.getPaymentId().startsWith(SIMULATED_PAYMENT_PREFIX)
                        && SIMULATED_SIGNATURE.equals(request.getSignature());
            }
            validateRazorpayCredentials();
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getOrderId());
            options.put("razorpay_payment_id", request.getPaymentId());
            options.put("razorpay_signature", request.getSignature());

            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (Exception e) {
            return false;
        }
    }

    private void validateRazorpayCredentials() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException("Razorpay credentials are not configured");
        }
    }

    public boolean isSimulationMode() {
        boolean isKeyIdEmpty = keyId == null || keyId.isBlank();
        boolean isKeySecretEmpty = keySecret == null || keySecret.isBlank();
        return isKeyIdEmpty && isKeySecretEmpty;
    }

    public double fetchOrderAmount(String orderId) throws RazorpayException {
        if (isSimulationMode()) {
            return 0.0; // In simulation mode, amount should be handled by caller
        }
        validateRazorpayCredentials();
        RazorpayClient client = new RazorpayClient(keyId, keySecret);
        Order order = client.orders.fetch(orderId);
        return ((Integer) order.get("amount")) / 100.0;
    }
}
