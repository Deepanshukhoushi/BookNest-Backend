package com.booknest.wishlistservice.service;

import com.booknest.wishlistservice.entity.Wishlist;

import java.util.List;

public interface WishlistService {
    Wishlist getWishlistByUser(Long userId);
    Wishlist addBook(Long userId, Long bookId);
    Wishlist removeBook(Long userId, Long bookId);
    void clearWishlist(Long userId);
    void moveToCart(Long userId, Long bookId);
    List<Wishlist> getAllWishlists();
}
