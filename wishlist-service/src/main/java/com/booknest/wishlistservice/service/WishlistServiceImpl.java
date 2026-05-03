package com.booknest.wishlistservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.booknest.wishlistservice.client.BookClient;
import com.booknest.wishlistservice.client.CartClient;
import com.booknest.wishlistservice.dto.BookDTO;
import com.booknest.wishlistservice.dto.CartRequest;
import com.booknest.wishlistservice.entity.Wishlist;
import com.booknest.wishlistservice.entity.WishlistItem;
import com.booknest.wishlistservice.repository.WishlistRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for managing user wishlists and moving items to the shopping cart.
 * Handles the logic for adding, removing, and transferring items between services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final BookClient bookClient;
    private final CartClient cartClient;

    // Fetches the user's wishlist or creates a new one if it doesn't exist
    @Override
    @Transactional
    public Wishlist getWishlistByUser(Long userId) {
        return wishlistRepository.findByUserId(userId)
                .orElseGet(() -> wishlistRepository.save(Wishlist.builder()
                        .userId(userId)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    // Adds a book to the user's wishlist and fetches its current details
    @Override
    @Transactional
    public Wishlist addBook(Long userId, Long bookId) {
        Wishlist wishlist = getWishlistByUser(userId);

        boolean alreadyExists = wishlist.getItems().stream()
                .anyMatch(item -> item.getBookId().equals(bookId));

        if (alreadyExists) {
            log.info("Book {} already in wishlist for user {}, returning existing wishlist.", bookId, userId);
            return wishlist;
        }

        BookDTO book = fetchBookDetails(bookId);

        WishlistItem item = WishlistItem.builder()
                .bookId(bookId)
                .bookTitle(book.getTitle())
                .bookPrice(book.getPrice() != null ? book.getPrice() : 0.0)
                .bookImageUrl(book.getCoverImageUrl())
                .wishlist(wishlist)
                .build();

        wishlist.getItems().add(item);
        return wishlistRepository.save(wishlist);
    }

    // Removes a specific book from the user's wishlist
    @Override
    @Transactional
    public Wishlist removeBook(Long userId, Long bookId) {
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wishlist not found"));

        wishlist.getItems().removeIf(item -> item.getBookId().equals(bookId));
        return wishlistRepository.save(wishlist);
    }

    // Deletes all items currently in the user's wishlist
    @Override
    @Transactional
    public void clearWishlist(Long userId) {
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wishlist not found"));
        wishlist.getItems().clear();
        wishlistRepository.save(wishlist);
    }

    // Transfers an item from the wishlist to the shopping cart
    @Override
    @Transactional
    public void moveToCart(Long userId, Long bookId) {
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wishlist not found"));

        WishlistItem targetItem = wishlist.getItems().stream()
                .filter(item -> item.getBookId().equals(bookId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Item not found in wishlist"));

        try {
            CartRequest request = CartRequest.builder()
                    .userId(userId)
                    .bookId(bookId)
                    .quantity(1)
                    .build();

            addToCart(request);
            
            // Only remove if cart add succeeded
            wishlist.getItems().remove(targetItem);
            wishlistRepository.save(wishlist);
            log.info("Successfully moved book {} from wishlist to cart for user {}", bookId, userId);
        } catch (Exception e) {
            log.error("Failed to move item to cart: {}", e.getMessage());
            throw new RuntimeException("Could not move item to cart. Cart-Service might be down.");
        }
    }

    // Helper to interact with the Cart Service via Feign client
    @CircuitBreaker(name = "cartService", fallbackMethod = "addToCartFallback")
    public void addToCart(CartRequest request) {
        cartClient.addToCart(request);
    }

    // Fallback logic when the Cart Service is unavailable
    public void addToCartFallback(CartRequest request, Throwable t) {
        log.error("Fallback for Cart-Service: {}", t.getMessage());
        throw new RuntimeException("Cart-Service is unavailable. Move to cart failed.");
    }

    // Helper to fetch details for a book from the Book Service
    @CircuitBreaker(name = "bookService", fallbackMethod = "fetchBookDetailsFallback")
    public BookDTO fetchBookDetails(Long bookId) {
        return bookClient.getBookById(bookId).getData();
    }

    // Fallback logic when the Book Service is unavailable
    public BookDTO fetchBookDetailsFallback(Long bookId, Throwable t) {
        log.error("Fallback for Book-Service: {}", t.getMessage());
        return BookDTO.builder()
                .bookId(bookId)
                .title("Unavailable Book")
                .price(0.0)
                .build();
    }

    // Admin utility to retrieve all wishlists in the system
	@Override
	public List<Wishlist> getAllWishlists() {
		return wishlistRepository.findAll();
	}
}
