package com.booknest.reviewservice.service.impl;

import com.booknest.reviewservice.client.OrderClient;
import com.booknest.reviewservice.dto.OrderDTO;
import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import com.booknest.reviewservice.repository.ReviewRepository;
import com.booknest.reviewservice.service.ReviewService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service implementation for managing book reviews and their moderation lifecycle.
 *
 * <h3>Moderation flow</h3>
 * <ol>
 *   <li>Customer submits a review → status set to {@link ReviewStatus#PENDING}.</li>
 *   <li>Admin views pending queue in the moderation dashboard.</li>
 *   <li>Admin approves or rejects; only {@link ReviewStatus#APPROVED} reviews are returned
 *       by the public-facing {@link #getByBook(Long)} method.</li>
 * </ol>
 *
 * <p>Ensures reviews are only submitted by verified purchasers via the Order Service.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderClient orderClient;

    // -------------------------------------------------------------------------
    // Customer operations
    // -------------------------------------------------------------------------

    /**
     * Submits a new review.
     * <ul>
     *   <li>Blocks duplicate reviews for the same book/user pair.</li>
     *   <li>Verifies the user has a delivered order containing the book.</li>
     *   <li>Sets status = {@link ReviewStatus#PENDING} so the review awaits admin approval.</li>
     * </ul>
     */
    @Override
    @Transactional
    public Review addReview(Review review) {
        // 1. Duplicate check
        Optional<Review> existing = reviewRepository.findByBookIdAndUserId(review.getBookId(), review.getUserId());
        if (existing.isPresent()) {
            throw new RuntimeException("You have already reviewed this book.");
        }

        // 2. Verified purchase check
        boolean hasPurchased = verifyPurchase(review.getUserId(), review.getBookId());
        if (!hasPurchased) {
            throw new RuntimeException("Only verified purchasers can submit a review.");
        }

        // 3. Sanitize comment against XSS
        if (review.getComment() != null) {
            review.setComment(Jsoup.clean(review.getComment(), Safelist.none()));
        }

        // 4. Set metadata and moderation status
        review.setVerified(true);
        review.setReviewDate(LocalDateTime.now());
        review.setStatus(ReviewStatus.PENDING);  // Always start as PENDING for admin to review

        log.info("New review submitted for bookId={} by userId={} – status set to PENDING", review.getBookId(), review.getUserId());
        return reviewRepository.save(review);
    }

    /** Returns the APPROVED reviews for a book (public-facing API). */
    @Override
    public List<Review> getByBook(Long bookId) {
        return reviewRepository.findByBookIdAndStatus(bookId, ReviewStatus.APPROVED);
    }

    /** Returns review written by a specific user (used for edit/delete ownership check). */
    @Override
    public List<Review> getByBookAndUserId(Long bookId, Long userId) {
        return reviewRepository.findByBookIdAndUserId(bookId, userId).map(List::of).orElse(List.of());
    }

    /** Returns all reviews by a specific user (for their dashboard). */
    @Override
    public List<Review> getByUser(Long userId) {
        return reviewRepository.findByUserId(userId);
    }

    /** Updates an existing review's rating and text; resets to PENDING for re-moderation. */
    @Override
    @Transactional
    public Review updateReview(Long reviewId, Review reviewRequest) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        review.setRating(reviewRequest.getRating());
        if (reviewRequest.getComment() != null) {
            review.setComment(Jsoup.clean(reviewRequest.getComment(), Safelist.none()));
        } else {
            review.setComment(null);
        }
        review.setReviewDate(LocalDateTime.now());
        review.setStatus(ReviewStatus.PENDING); // Requires re-moderation after edit

        log.info("Review {} updated – re-queued as PENDING", reviewId);
        return reviewRepository.save(review);
    }

    /** Deletes a review permanently. */
    @Override
    @Transactional
    public void deleteReview(Long reviewId) {
        reviewRepository.deleteById(reviewId);
        log.info("Review {} deleted", reviewId);
    }

    /** Calculates the average rating from only APPROVED reviews for a book. */
    @Override
    public Double getAvgRating(Long bookId) {
        Double avg = reviewRepository.findAvgRatingByBookId(bookId);
        return avg != null ? avg : 0.0;
    }

    // -------------------------------------------------------------------------
    // Admin moderation operations
    // -------------------------------------------------------------------------

    /** Returns ALL reviews regardless of status (admin full view). */
    @Override
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    /** Returns reviews filtered by a specific moderation status. */
    @Override
    public List<Review> getByStatus(ReviewStatus status) {
        return reviewRepository.findByStatus(status);
    }

    /** Approves a review, making it publicly visible. */
    @Override
    @Transactional
    public Review approveReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setStatus(ReviewStatus.APPROVED);
        log.info("Review {} APPROVED by admin", reviewId);
        return reviewRepository.save(review);
    }

    /** Rejects a review, hiding it from the public. */
    @Override
    @Transactional
    public Review rejectReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setStatus(ReviewStatus.REJECTED);
        log.info("Review {} REJECTED by admin", reviewId);
        return reviewRepository.save(review);
    }

    /** Returns a count map for each moderation status for use in admin badge display. */
    @Override
    public Map<ReviewStatus, Long> getStatusCounts() {
        Map<ReviewStatus, Long> counts = new EnumMap<>(ReviewStatus.class);
        for (ReviewStatus status : ReviewStatus.values()) {
            counts.put(status, reviewRepository.countByStatus(status));
        }
        return counts;
    }

    /** Fetches a single review by its ID. */
    @Override
    public Review getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Verifies the user has a completed order containing this book. */
    @CircuitBreaker(name = "orderService", fallbackMethod = "verifyPurchaseFallback")
    public boolean verifyPurchase(Long userId, Long bookId) {
        log.info("Verifying purchase for userId: {} and bookId: {}", userId, bookId);
        com.booknest.reviewservice.dto.ApiResponse<List<OrderDTO>> response = orderClient.getOrdersByUserId(userId);
        
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("Order service returned failure or null for userId: {}", userId);
            return false;
        }

        List<OrderDTO> orders = response.getData();
        log.info("Found {} orders for user {}", orders.size(), userId);
        
        boolean hasDelivered = orders.stream()
                .anyMatch(order -> {
                    boolean match = order.getBookId().equals(bookId) && "DELIVERED".equalsIgnoreCase(order.getOrderStatus());
                    if (order.getBookId().equals(bookId)) {
                        log.info("Found order for bookId={} with status={}", bookId, order.getOrderStatus());
                    }
                    return match;
                });

        if (!hasDelivered) {
            log.warn("No DELIVERED order found for userId={} and bookId={}", userId, bookId);
        }
        return hasDelivered;
    }

    /** Fallback when the order service is unavailable — defaults to not verified. */
    public boolean verifyPurchaseFallback(Long userId, Long bookId, Throwable t) {
        log.error("Fallback for Order-Service verification: {}", t.getMessage());
        return false;
    }
}
