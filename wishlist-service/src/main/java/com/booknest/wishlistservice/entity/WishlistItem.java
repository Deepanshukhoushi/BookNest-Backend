package com.booknest.wishlistservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlist_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a specific book entry in a wishlist.
 * Stores a snapshot of book details to avoid redundant catalog fetches.
 */
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @Column(nullable = false)
    private Long bookId;

    private String bookTitle;

    private Double bookPrice;

    private String bookImageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wishlist_id")
    @JsonIgnore
    private Wishlist wishlist;
}
