package com.booknest.orderservice.controller;

import com.booknest.orderservice.dto.PaymentVerifyRequest;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.service.OrderService;
import com.booknest.orderservice.service.RazorpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentResource.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private RazorpayService razorpayService;

    @MockitoBean
    private com.booknest.orderservice.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.booknest.orderservice.security.JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testInitiatePayment_Success() throws Exception {
        when(orderService.initiateRazorpayPayment(1L, 10L)).thenReturn("rzp_order_abc");

        mockMvc.perform(post("/api/v1/payments/initiate/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":10}")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("rzp_order_abc"));
    }

    @Test
    void testInitiatePayment_AdminBypass() throws Exception {
        when(orderService.initiateRazorpayPayment(42L, null)).thenReturn("rzp_admin_order");

        mockMvc.perform(post("/api/v1/payments/initiate/42")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rzp_admin_order"));
    }

    @Test
    void testInitiatePayment_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/payments/initiate/99")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetRazorpayPublicKey() throws Exception {
        when(razorpayService.getPublicKey()).thenReturn("rzp_test_key_123");

        mockMvc.perform(get("/api/v1/payments/config/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rzp_test_key_123"));
    }

    @Test
    void testVerifyPayment_Success() throws Exception {
        Order order = Order.builder().orderId(1L).orderStatus(OrderStatus.PAID).build();
        when(orderService.verifyRazorpayPayment(any(PaymentVerifyRequest.class), any()))
                .thenReturn(List.of(order));

        PaymentVerifyRequest req = new PaymentVerifyRequest("rzp_order_1", "pay_1", "sig_1", 10L);
        mockMvc.perform(post("/api/v1/payments/verify")
                .header("X-Auth-UserId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderStatus").value("PAID"));
    }

    @Test
    void testHandleWebhook_Success() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";

        mockMvc.perform(post("/api/v1/payments/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", "valid_sig")
                .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void testInitiatePayment_NoAuthHeaders_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/initiate/1"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void testInitiatePayment_RolePrefixAdminBypass() throws Exception {
        when(orderService.initiateRazorpayPayment(77L, null)).thenReturn("rzp_prefixed_admin");

        mockMvc.perform(post("/api/v1/payments/initiate/77")
                .header("X-Auth-Role", "ROLE_ADMIN")
                .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rzp_prefixed_admin"));
    }

    @Test
    void testInitiatePayment_InvalidAuthenticatedUserHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/initiate/1")
                .header("X-Auth-Role", "USER")
                .header("X-Auth-UserId", "abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testVerifyPayment_UsesAuthenticatedUserIdAttribute() throws Exception {
        Order order = Order.builder().orderId(2L).orderStatus(OrderStatus.PAID).build();
        when(orderService.verifyRazorpayPayment(any(PaymentVerifyRequest.class), eq(12L)))
                .thenReturn(List.of(order));

        PaymentVerifyRequest req = new PaymentVerifyRequest("rzp_order_2", "pay_2", "sig_2", 12L);
        mockMvc.perform(post("/api/v1/payments/verify")
                .requestAttr("authenticatedUserId", 12)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(2L));
    }
}
