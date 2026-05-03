package com.booknest.wishlistservice.repository;

import com.booknest.wishlistservice.entity.Wishlist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class WishlistRepositoryTest {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Test
    void testSaveAndFindByUserId() {
        Wishlist wishlist = Wishlist.builder()
                .userId(1L)
                .build();
        wishlistRepository.save(wishlist);

        Optional<Wishlist> found = wishlistRepository.findByUserId(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    void testExistsByUserId() {
        Wishlist wishlist = Wishlist.builder()
                .userId(2L)
                .build();
        wishlistRepository.save(wishlist);

        boolean exists = wishlistRepository.existsByUserId(2L);
        assertThat(exists).isTrue();
    }

    @Test
    void testDeleteByUserId() {
        Wishlist wishlist = Wishlist.builder()
                .userId(3L)
                .build();
        wishlistRepository.save(wishlist);

        wishlistRepository.deleteByUserId(3L);
        Optional<Wishlist> found = wishlistRepository.findByUserId(3L);
        assertThat(found).isEmpty();
    }

    // ── Additional repository coverage ────────────────────────────────────────

    @Test
    void testFindByUserId_NotFound() {
        Optional<Wishlist> found = wishlistRepository.findByUserId(9999L);
        assertThat(found).isEmpty();
    }

    @Test
    void testExistsByUserId_NotFound() {
        boolean exists = wishlistRepository.existsByUserId(8888L);
        assertThat(exists).isFalse();
    }

    @Test
    void testFindAll() {
        wishlistRepository.save(Wishlist.builder().userId(30L).build());
        wishlistRepository.save(Wishlist.builder().userId(31L).build());

        assertThat(wishlistRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testSave_updatesExistingWishlist() {
        Wishlist wishlist = Wishlist.builder().userId(40L).build();
        Wishlist saved = wishlistRepository.save(wishlist);

        saved.setUserId(40L); // same user, update
        Wishlist updated = wishlistRepository.save(saved);

        assertThat(updated.getWishlistId()).isEqualTo(saved.getWishlistId());
    }
}

