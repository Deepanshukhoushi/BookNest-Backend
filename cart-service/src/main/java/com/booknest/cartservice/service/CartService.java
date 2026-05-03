package com.booknest.cartservice.service;

import com.booknest.cartservice.entity.Cart;

import java.util.List;

public interface CartService {
    Cart getCartByUserId(Long userId);
    Cart addItem(Long userId, Long bookId, Integer quantity);
    Cart removeItem(Long userId, Long itemId);
    Cart updateQuantity(Long userId, Long itemId, Integer quantity);
    void clearCart(Long userId);
    Double calculateCartTotal(Long userId);
    List<Cart> getAllCarts();
}
