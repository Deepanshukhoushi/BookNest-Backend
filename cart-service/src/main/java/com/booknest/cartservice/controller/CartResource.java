package com.booknest.cartservice.controller;

import com.booknest.cartservice.dto.CartRequest;
import com.booknest.cartservice.entity.Cart;
import com.booknest.cartservice.payload.ApiResponse;
import com.booknest.cartservice.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * REST controller for managing the shopping cart endpoints.
 * Provides APIs to view, add, remove, and update items in a user's cart.
 */
@RestController
@RequestMapping("/api/v1/cart")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CartResource {

    private final CartService cartService;

    // Fetches the shopping cart for a specific user
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<Cart>> getCart(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Cart fetched successfully", cartService.getCartByUserId(userId)));
    }

    // Adds a volume to the cart or increments its quantity
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Cart>> addItem(
            @RequestBody(required = false) CartRequest cartRequest,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) Integer quantity,
            HttpServletRequest request) {
        cartRequest = mergeRequest(cartRequest, userId, bookId, null, quantity);
        enforceUserAccess(cartRequest.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Item added to cart successfully", cartService.addItem(cartRequest.getUserId(), cartRequest.getBookId(), cartRequest.getQuantity())));
    }

    // Removes an item from the cart based on its unique ID
    @DeleteMapping("/remove")
    public ResponseEntity<ApiResponse<Cart>> removeItem(
            @RequestBody(required = false) CartRequest cartRequest,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long itemId,
            HttpServletRequest request) {
        cartRequest = mergeRequest(cartRequest, userId, null, itemId, null);
        enforceUserAccess(cartRequest.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Item removed from cart successfully", cartService.removeItem(cartRequest.getUserId(), cartRequest.getItemId())));
    }

    // Updates the number of copies for an item in the cart
    @PutMapping("/update")
    public ResponseEntity<ApiResponse<Cart>> updateQuantity(
            @RequestBody(required = false) CartRequest cartRequest,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Integer quantity,
            HttpServletRequest request) {
        cartRequest = mergeRequest(cartRequest, userId, null, itemId, quantity);
        enforceUserAccess(cartRequest.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Cart quantity updated successfully", cartService.updateQuantity(cartRequest.getUserId(), cartRequest.getItemId(), cartRequest.getQuantity())));
    }

    // Clears all items from the user's cart using request body data
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @RequestBody(required = false) CartRequest cartRequest,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        cartRequest = mergeRequest(cartRequest, userId, null, null, null);
        enforceUserAccess(cartRequest.getUserId(), request);
        cartService.clearCart(cartRequest.getUserId());
        return ResponseEntity.ok(new ApiResponse<>(true, "Cart cleared successfully", null));
    }

    // Clears all items from the user's cart using the User ID path parameter
    @DeleteMapping("/clear/{userId}")
    public ResponseEntity<ApiResponse<Void>> clearCartByUserId(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        cartService.clearCart(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Cart cleared successfully", null));
    }

    // Admin feature: Retrieves every shopping cart in the system
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Cart>>> getAllCarts() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Carts fetched successfully", cartService.getAllCarts()));
    }

    // Returns the current total price of all items in the user's cart
    @GetMapping("/total/{userId}")
    public ResponseEntity<Double> calculateCartTotal(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(cartService.calculateCartTotal(userId));
    }

    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        if (targetUserId == null || request == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            return;
        }

        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private String resolveRole(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedRole");
        if (attribute instanceof String role && !role.isBlank()) {
            return role;
        }
        return request.getHeader("X-Auth-Role");
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedUserId");
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Integer userId) {
            return userId.longValue();
        }

        String userIdHeader = request.getHeader("X-Auth-UserId");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
        }
    }

    private boolean isAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        String normalized = roleHeader.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized);
    }

    private CartRequest mergeRequest(CartRequest body, Long userId, Long bookId, Long itemId, Integer quantity) {
        CartRequest merged = body != null ? body : new CartRequest();
        if (merged.getUserId() == null) {
            merged.setUserId(userId);
        }
        if (merged.getBookId() == null) {
            merged.setBookId(bookId);
        }
        if (merged.getItemId() == null) {
            merged.setItemId(itemId);
        }
        if (merged.getQuantity() == null) {
            merged.setQuantity(quantity);
        }
        if (merged.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return merged;
    }
}
