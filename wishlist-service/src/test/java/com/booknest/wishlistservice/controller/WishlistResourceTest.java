package com.booknest.wishlistservice.controller;

import com.booknest.wishlistservice.dto.WishlistRequest;
import com.booknest.wishlistservice.entity.Wishlist;
import com.booknest.wishlistservice.security.JwtAuthenticationFilter;
import com.booknest.wishlistservice.service.WishlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WishlistResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class WishlistResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishlistService wishlistService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetWishlist() throws Exception {
        Wishlist wishlist = Wishlist.builder().userId(1L).items(new ArrayList<>()).build();
        when(wishlistService.getWishlistByUser(1L)).thenReturn(wishlist);

        mockMvc.perform(get("/api/v1/wishlist/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void testAddBook() throws Exception {
        WishlistRequest request = new WishlistRequest(1L, 101L);
        Wishlist wishlist = Wishlist.builder().userId(1L).build();
        when(wishlistService.addBook(anyLong(), anyLong())).thenReturn(wishlist);

        mockMvc.perform(post("/api/v1/wishlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testRemoveBook() throws Exception {
        WishlistRequest request = new WishlistRequest(1L, 101L);
        Wishlist wishlist = Wishlist.builder().userId(1L).build();
        when(wishlistService.removeBook(anyLong(), anyLong())).thenReturn(wishlist);

        mockMvc.perform(delete("/api/v1/wishlist/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testClearWishlist() throws Exception {
        WishlistRequest request = new WishlistRequest(1L, null);

        mockMvc.perform(delete("/api/v1/wishlist/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testMoveToCart() throws Exception {
        WishlistRequest request = new WishlistRequest(1L, 101L);

        mockMvc.perform(post("/api/v1/wishlist/moveToCart")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetAllWishlists() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist/all")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist/2") // Requesting User 2
                .header("X-Auth-UserId", "1") // As User 1
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminBypass() throws Exception {
        Wishlist wishlist = Wishlist.builder().userId(2L).items(new ArrayList<>()).build();
        when(wishlistService.getWishlistByUser(2L)).thenReturn(wishlist);

        mockMvc.perform(get("/api/v1/wishlist/2")
                .header("X-Auth-UserId", "1") // As User 1
                .header("X-Auth-Role", "ADMIN")) // With ADMIN role
                .andExpect(status().isOk());
    }

    // ── Additional Branch Coverage Tests ───────────────────────────────────────

    @Test
    void testResolveUserId_InvalidNumberFormat() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist/1")
                .header("X-Auth-UserId", "not-a-number"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccess_WithRequestAttributes_Long() throws Exception {
        Wishlist wishlist = Wishlist.builder().userId(1L).items(new ArrayList<>()).build();
        when(wishlistService.getWishlistByUser(1L)).thenReturn(wishlist);

        mockMvc.perform(get("/api/v1/wishlist/1")
                .requestAttr("authenticatedUserId", 1L)
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testAccess_WithRequestAttributes_Integer() throws Exception {
        Wishlist wishlist = Wishlist.builder().userId(1L).items(new ArrayList<>()).build();
        when(wishlistService.getWishlistByUser(1L)).thenReturn(wishlist);

        mockMvc.perform(get("/api/v1/wishlist/1")
                .requestAttr("authenticatedUserId", 1) // Integer type
                .requestAttr("authenticatedRole", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testIsAdmin_WithRolePrefix() throws Exception {
        Wishlist wishlist = Wishlist.builder().userId(2L).items(new ArrayList<>()).build();
        when(wishlistService.getWishlistByUser(2L)).thenReturn(wishlist);

        mockMvc.perform(get("/api/v1/wishlist/2")
                .header("X-Auth-UserId", "1") // Mismatched user
                .header("X-Auth-Role", "ROLE_ADMIN")) // prefix
                .andExpect(status().isOk());
    }

    @Test
    void testIsAdmin_WithNullRole() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist/2")
                .header("X-Auth-UserId", "1")) // Missing role
                .andExpect(status().isForbidden());
    }
}
