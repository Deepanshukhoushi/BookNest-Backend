package com.booknest.reviewservice.controller;

import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import com.booknest.reviewservice.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * REST controller for the book review system, including public read endpoints
 * and admin-only moderation endpoints (approve / reject / filter by status).
 *
 * <h3>Public endpoints</h3>
 * <ul>
 *   <li>{@code GET /book/{bookId}} – returns only APPROVED reviews.</li>
 *   <li>{@code GET /avg/{bookId}}  – average rating from APPROVED reviews only.</li>
 * </ul>
 *
 * <h3>Admin-only moderation endpoints</h3>
 * <ul>
 *   <li>{@code GET  /all?status=PENDING} – fetch by status.</li>
 *   <li>{@code GET  /counts}             – badge counts per status.</li>
 *   <li>{@code PUT  /{id}/approve}       – approve a review.</li>
 *   <li>{@code PUT  /{id}/reject}        – reject a review.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reviews")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReviewResource {

    private final ReviewService reviewService;

    // -------------------------------------------------------------------------
    // Customer operations
    // -------------------------------------------------------------------------

    /** Submit a new review (verified purchasers only). Status starts as PENDING. */
    @PostMapping
    public ResponseEntity<Review> addReview(@jakarta.validation.Valid @RequestBody com.booknest.reviewservice.dto.ReviewRequest reviewRequest, HttpServletRequest request) {
        enforceUserAccess(reviewRequest.getUserId(), request);
        Review review = Review.builder()
                .bookId(reviewRequest.getBookId())
                .userId(reviewRequest.getUserId())
                .rating(reviewRequest.getRating())
                .comment(reviewRequest.getComment())
                .build();
        return ResponseEntity.ok(reviewService.addReview(review));
    }

    /**
     * Returns publicly visible (APPROVED) reviews for a book.
     * No authentication required – the filter is applied in the service layer.
     */
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<Review>> getByBook(@PathVariable Long bookId) {
        return ResponseEntity.ok(reviewService.getByBook(bookId));
    }

    /** Returns review by a specific user for a book. */
    @GetMapping("/book/{bookId}/user/{userId}")
    public ResponseEntity<List<Review>> getByBookAndUserId(@PathVariable Long bookId, @PathVariable Long userId) {
        return ResponseEntity.ok(reviewService.getByBookAndUserId(bookId, userId));
    }

    /** Returns reviews submitted by a specific user (for their dashboard). */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Review>> getByUser(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(reviewService.getByUser(userId));
    }

    /** Allows a user to edit their own review; the review is re-queued as PENDING afterwards. */
    @PutMapping("/{reviewId}")
    public ResponseEntity<Review> updateReview(@PathVariable Long reviewId, @jakarta.validation.Valid @RequestBody com.booknest.reviewservice.dto.ReviewRequest reviewRequest, HttpServletRequest request) {
        enforceReviewOwnerOrAdmin(reviewId, request);
        Review review = Review.builder()
                .bookId(reviewRequest.getBookId())
                .userId(reviewRequest.getUserId())
                .rating(reviewRequest.getRating())
                .comment(reviewRequest.getComment())
                .build();
        return ResponseEntity.ok(reviewService.updateReview(reviewId, review));
    }

    /** Allows a user or admin to delete a review. */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable Long reviewId, HttpServletRequest request) {
        enforceReviewOwnerOrAdmin(reviewId, request);
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }

    /** Public average rating for a book (computed from APPROVED reviews only). */
    @GetMapping("/avg/{bookId}")
    public ResponseEntity<Double> getAvgRating(@PathVariable Long bookId) {
        return ResponseEntity.ok(reviewService.getAvgRating(bookId));
    }

    // -------------------------------------------------------------------------
    // Admin moderation endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns all reviews, optionally filtered by moderation status.
     * {@code GET /api/v1/reviews/all?status=PENDING}
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Review>> getAllReviews(
            @RequestParam(required = false) ReviewStatus status) {
        List<Review> reviews = (status != null)
                ? reviewService.getByStatus(status)
                : reviewService.getAllReviews();
        return ResponseEntity.ok(reviews);
    }

    /**
     * Returns per-status counts for admin badge display without an extra list fetch.
     * Response: {@code {"PENDING": 3, "APPROVED": 12, "REJECTED": 1}}
     */
    @GetMapping("/counts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<ReviewStatus, Long>> getStatusCounts() {
        return ResponseEntity.ok(reviewService.getStatusCounts());
    }

    /** Approve a review – it immediately becomes publicly visible. */
    @PutMapping("/{reviewId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Review> approveReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewService.approveReview(reviewId));
    }

    /** Reject a review – it is hidden from the public. */
    @PutMapping("/{reviewId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Review> rejectReview(@PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewService.rejectReview(reviewId));
    }

    // -------------------------------------------------------------------------
    // Access-control helpers (identical pattern to other services)
    // -------------------------------------------------------------------------

    private void enforceReviewOwnerOrAdmin(Long reviewId, HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }
        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            return;
        }
        Review existingReview = reviewService.getReviewById(reviewId);
        if (!authenticatedUserId.equals(existingReview.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the review owner can modify this review");
        }
    }

    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        if (targetUserId == null || request == null) {
            return;
        }
        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }
        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            return;
        }
        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private String resolveRole(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedRole");
        if (attribute instanceof String role && !role.isBlank()) {
            return role;
        }
        return request.getHeader("X-Auth-Role");
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedUserId");
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Integer userId) {
            return userId.longValue();
        }
        String userIdHeader = request.getHeader("X-Auth-UserId");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
        }
    }

    private boolean isAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        String normalized = roleHeader.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized);
    }
}
