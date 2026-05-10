package com.booknest.orderservice.controller;

import com.booknest.orderservice.dto.CheckoutRequest;
import com.booknest.orderservice.dto.InvoiceResponse;
import com.booknest.orderservice.entity.Address;
import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.service.OrderService;
import com.booknest.orderservice.service.PdfInvoiceService;
import com.booknest.orderservice.service.RazorpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class OrderResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private RazorpayService razorpayService;

    @MockitoBean
    private PdfInvoiceService pdfInvoiceService;

    @MockitoBean
    private com.booknest.orderservice.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.booknest.orderservice.security.JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCheckout() throws Exception {
        Order order = Order.builder().orderId(1L).build();
        when(orderService.checkout(any(CheckoutRequest.class))).thenReturn(Collections.singletonList(order));

        CheckoutRequest request = CheckoutRequest.builder().userId(1L).paymentMethod("COD").build();
        mockMvc.perform(post("/api/v1/orders/checkout")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetOrdersByUserId() throws Exception {
        Order order = Order.builder().orderId(1L).userId(1L).build();
        when(orderService.getOrderByUserId(1L)).thenReturn(Collections.singletonList(order));

        mockMvc.perform(get("/api/v1/orders/user/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(1L));
    }

    @Test
    void testChangeStatus() throws Exception {
        Order order = Order.builder().orderId(1L).orderStatus(OrderStatus.DELIVERED).build();
        when(orderService.changeStatus(eq(1L), any())).thenReturn(order);

        mockMvc.perform(put("/api/v1/orders/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1, \"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("DELIVERED"));
    }

    @Test
    void testGetInvoice() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(Order.builder().userId(1L).build());
        when(orderService.getInvoice(1L)).thenReturn(InvoiceResponse.builder().build());

        mockMvc.perform(get("/api/v1/orders/1/invoice")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testDownloadInvoicePdf() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(Order.builder().userId(1L).build());
        when(orderService.getInvoice(1L)).thenReturn(InvoiceResponse.builder().invoiceNumber("INV-1").build());
        when(pdfInvoiceService.generateInvoicePdf(any())).thenReturn(new byte[0]);

        mockMvc.perform(get("/api/v1/orders/1/invoice/pdf")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void testAddressOperations() throws Exception {
        Address address = Address.builder()
                .addressId(1L)
                .customerId(1L)
                .fullName("John Doe")
                .mobileNumber("9876543210")
                .flatNumber("A-101")
                .city("New York")
                .state("NY")
                .pincode("123456")
                .build();
        when(orderService.storeAddress(any())).thenReturn(address);

        mockMvc.perform(post("/api/v1/orders/address")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(address)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetOrdersByUserId_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/user/999")
                .header("X-Auth-UserId", "1") // User 1 trying to access User 999
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetOrdersByUserId_AdminBypass() throws Exception {
        Order order = Order.builder().orderId(1L).userId(999L).build();
        when(orderService.getOrderByUserId(999L)).thenReturn(Collections.singletonList(order));

        mockMvc.perform(get("/api/v1/orders/user/999")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "ADMIN")) // Admin can access any user's orders
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testChangeStatus_AdminOnly() throws Exception {
        Order order = Order.builder().orderId(1L).orderStatus(OrderStatus.SHIPPED).build();
        when(orderService.changeStatus(anyLong(), any())).thenReturn(order);

        mockMvc.perform(put("/api/v1/orders/status")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1, \"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteOrder_AdminOnly() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ── Additional endpoint coverage ──────────────────────────────────────────

    @Test
    void testTrackOrder() throws Exception {
        Order order = Order.builder().orderId(3L).userId(1L).build();
        when(orderService.trackOrder(3L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/3/track")
                        .header("X-Auth-UserId", "1")
                        .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testCancelOrder() throws Exception {
        Order order = Order.builder().orderId(1L).userId(1L)
                .orderStatus(com.booknest.orderservice.enums.OrderStatus.CANCELLED).build();
        when(orderService.getOrderById(1L)).thenReturn(Order.builder().orderId(1L).userId(1L).build());
        when(orderService.cancelOrder(1L)).thenReturn(order);

        mockMvc.perform(put("/api/v1/orders/1/cancel")
                        .header("X-Auth-UserId", "1")
                        .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"));
    }

    @Test
    void testGetAllOrders_Admin() throws Exception {
        when(orderService.getAllOrders()).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetAllAddress_Admin() throws Exception {
        when(orderService.getAllAddress()).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/api/v1/orders/address")
                        .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAddressByCustomerId() throws Exception {
        com.booknest.orderservice.entity.Address addr = com.booknest.orderservice.entity.Address.builder()
                .addressId(1L).customerId(1L).fullName("John").build();
        when(orderService.getAddressByCustomerId(1L)).thenReturn(java.util.List.of(addr));

        mockMvc.perform(get("/api/v1/orders/address/1")
                        .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullName").value("John"));
    }

    @Test
    void testUpdateAddress() throws Exception {
        com.booknest.orderservice.entity.Address addr = com.booknest.orderservice.entity.Address.builder()
                .addressId(1L).customerId(1L).fullName("Jane Doe")
                .mobileNumber("9999999999").flatNumber("B-2")
                .city("Mumbai").state("MH").pincode("400001").build();
        when(orderService.getAddressById(1L)).thenReturn(addr);
        when(orderService.updateAddress(eq(1L), any())).thenReturn(addr);

        mockMvc.perform(put("/api/v1/orders/address/1")
                        .header("X-Auth-UserId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Jane Doe"));
    }

    @Test
    void testDeleteAddress() throws Exception {
        com.booknest.orderservice.entity.Address addr = com.booknest.orderservice.entity.Address.builder()
                .addressId(1L).customerId(1L).build();
        when(orderService.getAddressById(1L)).thenReturn(addr);

        mockMvc.perform(delete("/api/v1/orders/address/1")
                        .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void testCreateRazorpayOrder_Success() throws Exception {
        when(razorpayService.createOrder(anyDouble())).thenReturn("rzp_order_xyz");

        mockMvc.perform(post("/api/v1/orders/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rzp_order_xyz"));
    }

    @Test
    void testCreateRazorpayOrder_Failure() throws Exception {
        when(razorpayService.createOrder(anyDouble()))
                .thenThrow(new RuntimeException("Razorpay error"));

        mockMvc.perform(post("/api/v1/orders/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.0}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void testVerifyRazorpayPayment_Endpoint() throws Exception {
        com.booknest.orderservice.dto.PaymentVerifyRequest req =
                new com.booknest.orderservice.dto.PaymentVerifyRequest("rzp_1", "pay_1", "sig_1", 1L, null);
        when(razorpayService.verifySignature(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/orders/payment/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void testPlaceOrder_Endpoint() throws Exception {
        Order order = Order.builder().orderId(1L).userId(1L).build();
        when(orderService.checkout(any())).thenReturn(java.util.List.of(order));

        mockMvc.perform(post("/api/v1/orders/place")
                        .header("X-Auth-UserId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"paymentMethod\":\"COD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetOrderById_Endpoint() throws Exception {
        Order order = Order.builder().orderId(1L).userId(1L).build();
        when(orderService.getOrderById(1L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/1")
                        .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(1));
    }

    @Test
    void testEnforceUserAccess_NullUserId_Allowed() throws Exception {
        // When targetUserId is null, enforceUserAccess returns immediately
        Order order = Order.builder().orderId(1L).userId(null).build();
        when(orderService.getOrderById(1L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk());
    }
    @Test
    void testGetOrderById_InvalidAuthenticatedUserHeader_ReturnsUnauthorized() throws Exception {
        Order order = Order.builder().orderId(1L).userId(1L).build();
        when(orderService.getOrderById(1L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/1")
                .header("X-Auth-UserId", "oops"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetOrdersByUserId_UsesAuthenticatedAttributes() throws Exception {
        Order order = Order.builder().orderId(10L).userId(12L).build();
        when(orderService.getOrderByUserId(12L)).thenReturn(java.util.List.of(order));

        mockMvc.perform(get("/api/v1/orders/user/12")
                .requestAttr("authenticatedUserId", 12)
                .requestAttr("authenticatedRole", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(10L));
    }

    @Test
    void testDeleteAddress_ForbiddenForDifferentUser() throws Exception {
        Address addr = Address.builder()
                .addressId(1L)
                .customerId(2L)
                .build();
        when(orderService.getAddressById(1L)).thenReturn(addr);

        mockMvc.perform(delete("/api/v1/orders/address/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testChangeStatus_InvalidStatus_ReturnsError() throws Exception {
        mockMvc.perform(put("/api/v1/orders/status")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1, \"status\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangeStatus_MissingOrderId_ReturnsError() throws Exception {
        mockMvc.perform(put("/api/v1/orders/status")
                        .header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetOrderByUserId_MissingUserHeader_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/user/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDownloadInvoicePdf_OwnershipMismatch() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(Order.builder().userId(2L).build());

        mockMvc.perform(get("/api/v1/orders/1/invoice/pdf")
                        .header("X-Auth-UserId", "1")
                        .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}
