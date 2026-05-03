package com.booknest.cartservice.repository;

import com.booknest.cartservice.entity.Cart;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Test
    void testSaveAndFindByUserId() {
        Cart cart = Cart.builder().userId(1L).totalPrice(0.0).build();
        cartRepository.save(cart);

        Optional<Cart> found = cartRepository.findByUserId(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void testExistsByUserId() {
        Cart cart = Cart.builder().userId(2L).totalPrice(0.0).build();
        cartRepository.save(cart);

        assertThat(cartRepository.existsByUserId(2L)).isTrue();
        assertThat(cartRepository.existsByUserId(999L)).isFalse();
    }

    // ── Additional repository coverage ────────────────────────────────────────

    @Test
    void testFindByUserIdForUpdate() {
        Cart cart = Cart.builder().userId(10L).totalPrice(50.0).build();
        cartRepository.saveAndFlush(cart);

        Optional<Cart> found = cartRepository.findByUserIdForUpdate(10L);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(10L);
        assertThat(found.get().getTotalPrice()).isEqualTo(50.0);
    }

    @Test
    void testFindByUserIdForUpdate_NotFound() {
        Optional<Cart> found = cartRepository.findByUserIdForUpdate(9999L);
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByUserId_NotFound() {
        Optional<Cart> found = cartRepository.findByUserId(8888L);
        assertThat(found).isEmpty();
    }

    @Test
    void testFindAll() {
        cartRepository.save(Cart.builder().userId(20L).totalPrice(0.0).build());
        cartRepository.save(Cart.builder().userId(21L).totalPrice(0.0).build());

        assertThat(cartRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}

