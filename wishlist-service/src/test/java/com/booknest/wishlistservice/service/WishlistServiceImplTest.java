package com.booknest.wishlistservice.service;

import com.booknest.wishlistservice.client.BookClient;
import com.booknest.wishlistservice.client.CartClient;
import com.booknest.wishlistservice.dto.BookDTO;
import com.booknest.wishlistservice.dto.CartRequest;
import com.booknest.wishlistservice.entity.Wishlist;
import com.booknest.wishlistservice.entity.WishlistItem;
import com.booknest.wishlistservice.payload.ApiResponse;
import com.booknest.wishlistservice.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private BookClient bookClient;

    @Mock
    private CartClient cartClient;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    private Wishlist testWishlist;
    private final Long userId = 1L;
    private final Long bookId = 101L;

    @BeforeEach
    void setUp() {
        testWishlist = Wishlist.builder()
                .wishlistId(1L)
                .userId(userId)
                .items(new ArrayList<>())
                .build();
    }

    @Test
    void testGetWishlistByUser_Existing() {
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));
        Wishlist result = wishlistService.getWishlistByUser(userId);
        assertThat(result).isEqualTo(testWishlist);
    }

    @Test
    void testGetWishlistByUser_New() {
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(wishlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wishlist result = wishlistService.getWishlistByUser(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void testAddBook_Success() {
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", BookDTO.builder()
                .bookId(bookId).title("Book").price(20.0).build()));
        when(wishlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wishlist result = wishlistService.addBook(userId, bookId);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getBookId()).isEqualTo(bookId);
    }

    @Test
    void testAddBook_AlreadyExists() {
        testWishlist.getItems().add(WishlistItem.builder().bookId(bookId).build());
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));

        Wishlist result = wishlistService.addBook(userId, bookId);

        assertThat(result.getItems()).hasSize(1);
        verify(bookClient, never()).getBookById(anyLong());
    }

    @Test
    void testRemoveBook() {
        testWishlist.getItems().add(WishlistItem.builder().bookId(bookId).build());
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));
        when(wishlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wishlist result = wishlistService.removeBook(userId, bookId);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void testClearWishlist() {
        testWishlist.getItems().add(WishlistItem.builder().bookId(bookId).build());
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));

        wishlistService.clearWishlist(userId);

        assertThat(testWishlist.getItems()).isEmpty();
        verify(wishlistRepository).save(testWishlist);
    }

    @Test
    void testMoveToCart_Success() {
        WishlistItem item = WishlistItem.builder().bookId(bookId).build();
        testWishlist.getItems().add(item);
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));

        wishlistService.moveToCart(userId, bookId);

        verify(cartClient).addToCart(any(CartRequest.class));
        assertThat(testWishlist.getItems()).isEmpty();
        verify(wishlistRepository).save(testWishlist);
    }

    @Test
    void testMoveToCart_Fail() {
        WishlistItem item = WishlistItem.builder().bookId(bookId).build();
        testWishlist.getItems().add(item);
        when(wishlistRepository.findByUserId(userId)).thenReturn(Optional.of(testWishlist));
        doThrow(new RuntimeException("Cart down")).when(cartClient).addToCart(any());

        assertThatThrownBy(() -> wishlistService.moveToCart(userId, bookId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Could not move item to cart");

        assertThat(testWishlist.getItems()).hasSize(1); // Not removed
    }

    @Test
    void testFetchBookDetailsFallback() {
        BookDTO result = wishlistService.fetchBookDetailsFallback(bookId, new RuntimeException("Error"));
        assertThat(result.getTitle()).isEqualTo("Unavailable Book");
        assertThat(result.getBookId()).isEqualTo(bookId);
    }

    @Test
    void testAddToCartFallback() {
        assertThatThrownBy(() -> wishlistService.addToCartFallback(new CartRequest(), new RuntimeException("Down")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cart-Service is unavailable");
    }

    @Test
    void testGetAllWishlists() {
        when(wishlistRepository.findAll()).thenReturn(new ArrayList<>());
        wishlistService.getAllWishlists();
        verify(wishlistRepository).findAll();
    }

    @Test
    void testFetchBookDetails_Success() {
        when(bookClient.getBookById(bookId)).thenReturn(new ApiResponse<>(true, "ok", BookDTO.builder()
                .bookId(bookId).title("Book").build()));
        BookDTO result = wishlistService.fetchBookDetails(bookId);
        assertThat(result.getTitle()).isEqualTo("Book");
    }

    @Test
    void testAddToCart_Success() {
        CartRequest request = new CartRequest();
        wishlistService.addToCart(request);
        verify(cartClient).addToCart(request);
    }

    // ── Additional branch coverage ────────────────────────────────────────────

    @Test
    void testRemoveBook_WishlistNotFound() {
        when(wishlistRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> wishlistService.removeBook(userId, bookId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Wishlist not found");
    }

    @Test
    void testClearWishlist_WishlistNotFound() {
        when(wishlistRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> wishlistService.clearWishlist(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Wishlist not found");
    }

    @Test
    void testMoveToCart_ItemNotInWishlist() {
        // Wishlist exists but doesn't contain the requested book
        when(wishlistRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(testWishlist));

        assertThatThrownBy(() -> wishlistService.moveToCart(userId, bookId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Item not found in wishlist");
    }

    @Test
    void testAddBook_NullBookPrice_DefaultsToZero() {
        BookDTO book = com.booknest.wishlistservice.dto.BookDTO.builder()
                .bookId(bookId).title("Free Book").price(null).build();
        when(wishlistRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(testWishlist));
        when(bookClient.getBookById(bookId)).thenReturn(
                new com.booknest.wishlistservice.payload.ApiResponse<>(true, "ok", book));
        when(wishlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Wishlist result = wishlistService.addBook(userId, bookId);

        assertThat(result.getItems().get(0).getBookPrice()).isEqualTo(0.0);
    }
}

