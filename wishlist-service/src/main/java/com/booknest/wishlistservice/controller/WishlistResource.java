package com.booknest.wishlistservice.controller;

import com.booknest.wishlistservice.dto.WishlistRequest;
import com.booknest.wishlistservice.payload.ApiResponse;
import com.booknest.wishlistservice.entity.Wishlist;
import com.booknest.wishlistservice.service.WishlistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * REST controller for managing user wishlists.
 * Provides endpoints for adding/removing books, clearing wishlists,
 * and moving items to cart. Includes security checks to ensure
 * users can only access their own wishlists (admins can access all).
 */
@RestController
@RequestMapping("/api/v1/wishlist")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WishlistResource {

    private final WishlistService wishlistService;

    // Retrieves the wishlist for a specific user
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<Wishlist>> getWishlist(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Wishlist fetched successfully", wishlistService.getWishlistByUser(userId)));
    }

    // Adds a book to the user's wishlist
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Wishlist>> addBook(@RequestBody WishlistRequest wishlistRequest, HttpServletRequest request) {
        enforceUserAccess(wishlistRequest.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Item added to wishlist successfully", wishlistService.addBook(wishlistRequest.getUserId(), wishlistRequest.getBookId())));
    }

    // Removes a book from the user's wishlist
    @DeleteMapping("/remove")
    public ResponseEntity<ApiResponse<Wishlist>> removeBook(@RequestBody WishlistRequest wishlistRequest, HttpServletRequest request) {
        enforceUserAccess(wishlistRequest.getUserId(), request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Item removed from wishlist successfully", wishlistService.removeBook(wishlistRequest.getUserId(), wishlistRequest.getBookId())));
    }

    // Clears all items from the user's wishlist
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearWishlist(@RequestBody WishlistRequest wishlistRequest, HttpServletRequest request) {
        enforceUserAccess(wishlistRequest.getUserId(), request);
        wishlistService.clearWishlist(wishlistRequest.getUserId());
        return ResponseEntity.ok(new ApiResponse<>(true, "Wishlist cleared successfully", null));
    }

    // Moves a book from wishlist to cart
    @PostMapping("/moveToCart")
    public ResponseEntity<ApiResponse<Void>> moveToCart(@RequestBody WishlistRequest wishlistRequest, HttpServletRequest request) {
        enforceUserAccess(wishlistRequest.getUserId(), request);
        wishlistService.moveToCart(wishlistRequest.getUserId(), wishlistRequest.getBookId());
        return ResponseEntity.ok(new ApiResponse<>(true, "Item moved to cart successfully", null));
    }

    // Admin endpoint: retrieves all wishlists (no user restriction)
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Wishlist>>> getAllWishlists() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Wishlists fetched successfully", wishlistService.getAllWishlists()));
    }

    // Security method: ensures users can only access their own resources (admins bypass)
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
}
