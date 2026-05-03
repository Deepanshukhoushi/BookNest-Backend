package com.booknest.cartservice.controller;

import com.booknest.cartservice.dto.CartRequest;
import com.booknest.cartservice.entity.Cart;
import com.booknest.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class CartResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private com.booknest.cartservice.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.booknest.cartservice.security.JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetCart() throws Exception {
        Cart cart = Cart.builder().userId(1L).build();
        when(cartService.getCartByUserId(1L)).thenReturn(cart);

        mockMvc.perform(get("/api/v1/cart/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L));
    }

    @Test
    void testAddItem() throws Exception {
        Cart cart = Cart.builder().userId(1L).build();
        when(cartService.addItem(eq(1L), eq(101L), eq(2))).thenReturn(cart);

        CartRequest cartRequest = new CartRequest();
        cartRequest.setUserId(1L);
        cartRequest.setBookId(101L);
        cartRequest.setQuantity(2);

        mockMvc.perform(post("/api/v1/cart/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cartRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testRemoveItem() throws Exception {
        Cart cart = Cart.builder().userId(1L).build();
        when(cartService.removeItem(1L, 100L)).thenReturn(cart);

        mockMvc.perform(delete("/api/v1/cart/remove")
                .param("userId", "1")
                .param("itemId", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void testClearCart() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/clear/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateQuantity() throws Exception {
        Cart cart = Cart.builder().userId(1L).build();
        when(cartService.updateQuantity(anyLong(), anyLong(), anyInt())).thenReturn(cart);

        mockMvc.perform(put("/api/v1/cart/update")
                .param("userId", "1")
                .param("itemId", "100")
                .param("quantity", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void testCalculateCartTotal() throws Exception {
        when(cartService.calculateCartTotal(1L)).thenReturn(150.0);

        mockMvc.perform(get("/api/v1/cart/total/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("150.0"));
    }

    @Test
    void testGetAllCarts() throws Exception {
        mockMvc.perform(get("/api/v1/cart/all"))
                .andExpect(status().isOk());
    }

    // ── Additional Branch Coverage Tests ───────────────────────────────────────

    @Test
    void testAddItem_MissingUserId_ThrowsBadRequest() throws Exception {
        // Omitting userId to trigger mergeRequest validation exception
        mockMvc.perform(post("/api/v1/cart/add")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetCart_Forbidden() throws Exception {
        // User 2 trying to access User 1's cart
        mockMvc.perform(get("/api/v1/cart/1")
                .header("X-Auth-UserId", "2")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetCart_AdminBypass() throws Exception {
        when(cartService.getCartByUserId(1L)).thenReturn(Cart.builder().userId(1L).build());

        mockMvc.perform(get("/api/v1/cart/1")
                .header("X-Auth-UserId", "99")
                .header("X-Auth-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testResolveUserId_InvalidNumberFormat() throws Exception {
        mockMvc.perform(get("/api/v1/cart/1")
                .header("X-Auth-UserId", "not-a-number"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccess_WithRequestAttributes_Long() throws Exception {
        when(cartService.getCartByUserId(1L)).thenReturn(Cart.builder().userId(1L).build());

        mockMvc.perform(get("/api/v1/cart/1")
                .requestAttr("authenticatedUserId", 1L)
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testAccess_WithRequestAttributes_Integer() throws Exception {
        when(cartService.getCartByUserId(1L)).thenReturn(Cart.builder().userId(1L).build());

        mockMvc.perform(get("/api/v1/cart/1")
                .requestAttr("authenticatedUserId", 1) // Integer
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testClearCart_WithBody() throws Exception {
        mockMvc.perform(delete("/api/v1/cart/clear")
                .contentType("application/json")
                .content("{\"userId\": 1}")
                .header("X-Auth-UserId", "1"))
                .andExpect(status().isOk());
    }
}
