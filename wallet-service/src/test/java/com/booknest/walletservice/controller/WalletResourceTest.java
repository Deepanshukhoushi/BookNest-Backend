package com.booknest.walletservice.controller;

import com.booknest.walletservice.dto.WalletRequest;
import com.booknest.walletservice.entity.Wallet;
import com.booknest.walletservice.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(WalletResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class WalletResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private com.booknest.walletservice.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.booknest.walletservice.security.JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

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
    void testGetWallets() throws Exception {
        when(walletService.getWallets()).thenReturn(Collections.singletonList(testWallet));

        mockMvc.perform(get("/api/v1/wallet/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].walletId").value(walletId));
    }

    @Test
    void testGetByUserId() throws Exception {
        when(walletService.getByUserId(userId)).thenReturn(testWallet);

        mockMvc.perform(get("/api/v1/wallet/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));
    }

    @Test
    void testAddMoney() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        when(walletService.addMoney(eq(walletId), anyDouble(), anyString())).thenReturn(testWallet);

        WalletRequest request = new WalletRequest(userId, walletId, 200.0, null, "test");
        mockMvc.perform(post("/api/v1/wallet/addMoney")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testPayMoney() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        when(walletService.payMoney(anyLong(), anyDouble(), anyLong())).thenReturn(testWallet);

        WalletRequest request = new WalletRequest(userId, walletId, 100.0, 1L, null);
        mockMvc.perform(post("/api/v1/wallet/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testInitiateRazorpayTopUp() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        when(walletService.initiateRazorpayTopUp(any())).thenReturn("rzp_order_1");

        WalletRequest request = new WalletRequest(userId, walletId, 200.0, null, null);
        mockMvc.perform(post("/api/v1/wallet/initiate-razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rzp_order_1"));
    }

    @Test
    void testVerifyRazorpayTopUp() throws Exception {
        when(walletService.verifyRazorpayTopUp(any(), anyLong())).thenReturn(testWallet);

        mockMvc.perform(post("/api/v1/wallet/verify-razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"o1\", \"paymentId\":\"p1\", \"signature\":\"s1\", \"amount\":200.0}")
                .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetById_Forbidden() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        
        // User 2 trying to access User 1's wallet
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "2")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetById_AdminBypass() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "99")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetStatements() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        when(walletService.getStatementsByWalletId(walletId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/wallet/statements/" + walletId)
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteById() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);

        mockMvc.perform(delete("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testInitializeWallet() throws Exception {
        when(walletService.initializeWallet(userId)).thenReturn(testWallet);

        mockMvc.perform(post("/api/v1/wallet/initialize/" + userId)
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetRazorpayPublicKey() throws Exception {
        when(walletService.getRazorpayPublicKey()).thenReturn("pk_test");

        mockMvc.perform(get("/api/v1/wallet/razorpay-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pk_test"));
    }

    @Test
    void testAddWallet() throws Exception {
        when(walletService.addWallet(any())).thenReturn(testWallet);

        mockMvc.perform(post("/api/v1/wallet/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testWallet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Wallet created successfully"));
    }

    // ── Additional Branch Coverage Tests ───────────────────────────────────────

    @Test
    void testVerifyRazorpayTopUp_NoUserContext() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/verify-razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"o1\", \"paymentId\":\"p1\", \"signature\":\"s1\", \"amount\":200.0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testResolveUserId_InvalidNumberFormat() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "not-a-number"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccess_WithRequestAttributes_Long() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .requestAttr("authenticatedUserId", 1L)
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testAccess_WithRequestAttributes_Integer() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .requestAttr("authenticatedUserId", 1) // Integer
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testIsAdmin_WithRolePrefix() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "2") // Mismatched user
                .header("X-Auth-Role", "ROLE_ADMIN")) // Should bypass with prefix
                .andExpect(status().isOk());
    }

    @Test
    void testIsAdmin_WithNullRole() throws Exception {
        when(walletService.getById(walletId)).thenReturn(testWallet);
        mockMvc.perform(get("/api/v1/wallet/" + walletId)
                .header("X-Auth-UserId", "2")) // Mismatched user, no role
                .andExpect(status().isForbidden());
    }
}

