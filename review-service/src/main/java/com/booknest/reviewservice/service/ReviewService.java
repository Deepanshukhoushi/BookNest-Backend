package com.booknest.reviewservice.service;

import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;

import java.util.List;
import java.util.Map;

/** Service contract for managing book reviews and their moderation lifecycle. */
public interface ReviewService {
    Review addReview(Review review);
    List<Review> getByBookAndUserId(Long bookId, Long userId);
    List<Review> getByBook(Long bookId);
    List<Review> getByUser(Long userId);
    Review updateReview(Long reviewId, Review review);
    void deleteReview(Long reviewId);
    Double getAvgRating(Long bookId);
    List<Review> getAllReviews();
    Review getReviewById(Long reviewId);

    // Moderation operations (admin-only)
    /** Returns all reviews matching a specific moderation status. */
    List<Review> getByStatus(ReviewStatus status);

    /** Approves a review, making it publicly visible. */
    Review approveReview(Long reviewId);

    /** Rejects a review, hiding it from the public. */
    Review rejectReview(Long reviewId);

    /** Returns badge counts for each moderation status (PENDING / APPROVED / REJECTED). */
    Map<ReviewStatus, Long> getStatusCounts();
}

