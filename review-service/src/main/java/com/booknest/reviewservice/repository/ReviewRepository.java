package com.booknest.reviewservice.repository;

import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing book review data.
 * Includes specialized queries for retrieving user feedback, moderation filtering,
 * and calculating aggregate ratings.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByBookId(Long bookId);

    List<Review> findByUserId(Long userId);

    Optional<Review> findByBookIdAndUserId(Long bookId, Long userId);

    /** Returns only APPROVED reviews for a specific book (used by public API). */
    List<Review> findByBookIdAndStatus(Long bookId, ReviewStatus status);

    /** Returns all reviews with a specific moderation status (used by admin). */
    List<Review> findByStatus(ReviewStatus status);

    /** Computes the average star rating for a particular volume (only APPROVED reviews count). */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.bookId = ?1 AND r.status = 'APPROVED'")
    Double findAvgRatingByBookId(Long bookId);

    long countByBookId(Long bookId);

    /** Returns a count per moderation status for admin badge display. */
    long countByStatus(ReviewStatus status);
}

