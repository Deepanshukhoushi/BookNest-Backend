package com.booknest.cartservice.service;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.booknest.cartservice.client.BookClient;
import com.booknest.cartservice.dto.BookDTO;
import com.booknest.cartservice.entity.Cart;
import com.booknest.cartservice.entity.CartItem;
import com.booknest.cartservice.payload.ApiResponse;
import com.booknest.cartservice.repository.CartRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for managing the shopping cart with Redis caching.
 * Handles adding/removing items, updating quantities, and calculating totals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final BookClient bookClient;

    // Retrieves the shopping cart for a specific user, using Redis cache if available
    @Override
    @Cacheable(value = "cart", key = "#userId")
    public Cart getCartByUserId(Long userId) {
        log.info("Fetching cart from Database for userId: {}", userId);
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> Cart.builder().userId(userId).build());
    }

    // Adds a book to the cart and synchronizes its latest price and details
    @Override
    @Transactional
    @CacheEvict(value = "cart", key = "#userId")
    public Cart addItem(Long userId, Long bookId, Integer quantity) {
        BookDTO book = fetchBookDetails(bookId);
        validateRequestedQuantity(quantity);
        Cart cart = getOrCreateCartForUpdate(userId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getBookId().equals(bookId))
                .findFirst();

        if (existingItem.isPresent()) {
            int nextQuantity = existingItem.get().getQuantity() + quantity;
            validateQuantityAgainstStock(book, nextQuantity);
            existingItem.get().setQuantity(nextQuantity);
            existingItem.get().setPrice(book.getPrice() != null ? book.getPrice() : 0.0); // Always sync latest price
            existingItem.get().setBookImageUrl(book.getCoverImageUrl());
        } else {
            validateQuantityAgainstStock(book, quantity);
            CartItem newItem = CartItem.builder()
                    .bookId(bookId)
                    .bookTitle(book.getTitle())
                    .price(book.getPrice() != null ? book.getPrice() : 0.0)
                    .quantity(quantity)
                    .bookImageUrl(book.getCoverImageUrl())
                    .cart(cart)
                    .build();
            cart.getItems().add(newItem);
        }

        updateTotalPrice(cart);
        return cartRepository.save(cart);
    }

    // Removes an individual item from the user's shopping cart
    @Override
    @Transactional
    @CacheEvict(value = "cart", key = "#userId")
    public Cart removeItem(Long userId, Long itemId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.getItems().removeIf(item -> item.getItemId().equals(itemId));

        updateTotalPrice(cart);
        return cartRepository.save(cart);
    }

    // Modifies the quantity of an existing cart item and updates the total
    @Override
    @Transactional
    @CacheEvict(value = "cart", key = "#userId")
    public Cart updateQuantity(Long userId, Long itemId, Integer quantity) {
        validateRequestedQuantity(quantity);
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not found in cart"));

        BookDTO book = fetchBookDetails(item.getBookId());
        validateQuantityAgainstStock(book, quantity);
        item.setQuantity(quantity);
        item.setPrice(book.getPrice()); // Sync price on quantity update
        item.setBookImageUrl(book.getCoverImageUrl());

        updateTotalPrice(cart);
        return cartRepository.save(cart);
    }

    // Empties the entire shopping cart for the user
    @Override
    @Transactional
    @CacheEvict(value = "cart", key = "#userId")
    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.getItems().clear();
        cart.setTotalPrice(0.0);
        cartRepository.save(cart);
    }

    // Returns the current total price of all items in the user's cart
    @Override
    public Double calculateCartTotal(Long userId) {
        Cart cart = getCartByUserId(userId);
        return cart.getTotalPrice();
    }

    // Admin utility to retrieve every cart in the database
    @Override
    public List<Cart> getAllCarts() {
        return cartRepository.findAll();
    }

    // Helper to fetch book information from the Book Service via Feign client
    @CircuitBreaker(name = "bookService", fallbackMethod = "fetchBookDetailsFallback")
    public BookDTO fetchBookDetails(Long bookId) {
        log.info("Fetching book details for ID: {}", bookId);
        ApiResponse<BookDTO> response = bookClient.getBookById(bookId);
        return response.getData();
    }

    // Fallback logic when the Book Service is unavailable
    public BookDTO fetchBookDetailsFallback(Long bookId, Throwable t) {
        log.error("Fallback triggered for Book-Service: {}", t.getMessage());
        return BookDTO.builder()
                .bookId(bookId)
                .title("Service Unavailable")
                .price(0.0)
                .build();
    }

    private void updateTotalPrice(Cart cart) {
        double total = cart.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        cart.setTotalPrice(total);
    }

    private Cart getOrCreateCartForUpdate(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    cartRepository.saveAndFlush(Cart.builder().userId(userId).build());
                    return cartRepository.findByUserIdForUpdate(userId)
                            .orElseThrow(() -> new RuntimeException("Cart could not be created"));
                });
    }

    private void validateRequestedQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new RuntimeException("Quantity must be at least 1");
        }
        if (quantity > 99) {
            throw new RuntimeException("Quantity cannot exceed 99 units per request");
        }
    }

    private void validateQuantityAgainstStock(BookDTO book, int desiredQuantity) {
        if (book == null) {
            throw new RuntimeException("Book details could not be loaded");
        }
        if (book.getStock() != null && desiredQuantity > book.getStock()) {
            throw new RuntimeException("Requested quantity exceeds available stock");
        }
    }
}
