package com.booknest.reviewservice.service.impl;

import com.booknest.reviewservice.client.OrderClient;
import com.booknest.reviewservice.dto.ApiResponse;
import com.booknest.reviewservice.dto.OrderDTO;
import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import com.booknest.reviewservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderClient orderClient;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Review testReview;
    private final Long userId = 1L;
    private final Long bookId = 101L;

    @BeforeEach
    void setUp() {
        testReview = Review.builder()
                .reviewId(1L)
                .userId(userId)
                .bookId(bookId)
                .rating(5)
                .comment("Great book!")
                .status(ReviewStatus.PENDING)
                .build();
    }

    @Test
    void testAddReview_Success() {
        when(reviewRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.empty());
        
        OrderDTO order = new OrderDTO();
        order.setBookId(bookId);
        order.setOrderStatus("DELIVERED");
        
        ApiResponse<List<OrderDTO>> response = new ApiResponse<>(true, "ok", Collections.singletonList(order));
        when(orderClient.getOrdersByUserId(userId)).thenReturn(response);
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review result = reviewService.addReview(testReview);

        assertThat(result.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(result.getVerified()).isTrue();
    }

    @Test
    void testAddReview_NotPurchased() {
        when(reviewRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.empty());
        when(orderClient.getOrdersByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", Collections.emptyList()));

        assertThatThrownBy(() -> reviewService.addReview(testReview))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only verified purchasers");
    }

    @Test
    void testApproveReview() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review result = reviewService.approveReview(1L);

        assertThat(result.getStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void testRejectReview() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review result = reviewService.rejectReview(1L);

        assertThat(result.getStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void testGetAvgRating() {
        when(reviewRepository.findAvgRatingByBookId(bookId)).thenReturn(4.5);
        Double avg = reviewService.getAvgRating(bookId);
        assertThat(avg).isEqualTo(4.5);
    }

    @Test
    void testGetAvgRating_Null() {
        when(reviewRepository.findAvgRatingByBookId(bookId)).thenReturn(null);
        Double avg = reviewService.getAvgRating(bookId);
        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    void testVerifyPurchaseFallback() {
        boolean result = reviewService.verifyPurchaseFallback(userId, bookId, new RuntimeException("Error"));
        assertThat(result).isFalse();
    }

    @Test
    void testUpdateReview() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review updateRequest = Review.builder().rating(4).comment("<script>alert('xss')</script>Updated").build();
        Review result = reviewService.updateReview(1L, updateRequest);

        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getComment()).isEqualTo("Updated"); // XSS Sanitized
        assertThat(result.getStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void testDeleteReview() {
        doNothing().when(reviewRepository).deleteById(1L);
        reviewService.deleteReview(1L);
        verify(reviewRepository).deleteById(1L);
    }

    @Test
    void testGetReviewsByBookId() {
        when(reviewRepository.findByBookIdAndStatus(bookId, ReviewStatus.APPROVED))
                .thenReturn(Collections.singletonList(testReview));
        List<Review> result = reviewService.getByBook(bookId);
        assertThat(result).hasSize(1);
    }

    @Test
    void testGetReviewsByUserId() {
        when(reviewRepository.findByUserId(userId)).thenReturn(Collections.singletonList(testReview));
        List<Review> result = reviewService.getByUser(userId);
        assertThat(result).hasSize(1);
    }

    @Test
    void testGetByBookAndUserId() {
        when(reviewRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.of(testReview));
        List<Review> result = reviewService.getByBookAndUserId(bookId, userId);
        assertThat(result).hasSize(1);
    }

    @Test
    void testGetAllReviews() {
        when(reviewRepository.findAll()).thenReturn(Collections.emptyList());
        List<Review> result = reviewService.getAllReviews();
        assertThat(result).isEmpty();
    }

    @Test
    void testGetByStatus() {
        when(reviewRepository.findByStatus(ReviewStatus.PENDING)).thenReturn(Collections.emptyList());
        List<Review> result = reviewService.getByStatus(ReviewStatus.PENDING);
        assertThat(result).isEmpty();
    }

    @Test
    void testGetStatusCounts() {
        when(reviewRepository.countByStatus(any())).thenReturn(5L);
        Map<ReviewStatus, Long> counts = reviewService.getStatusCounts();
        assertThat(counts.get(ReviewStatus.PENDING)).isEqualTo(5L);
    }

    @Test
    void testGetReviewById() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        Review result = reviewService.getReviewById(1L);
        assertThat(result.getReviewId()).isEqualTo(1L);
    }

    @Test
    void testVerifyPurchase() {
        OrderDTO order = new OrderDTO();
        order.setBookId(bookId);
        order.setOrderStatus("DELIVERED");
        ApiResponse<List<OrderDTO>> response = new ApiResponse<>(true, "ok", Collections.singletonList(order));
        when(orderClient.getOrdersByUserId(userId)).thenReturn(response);

        boolean result = reviewService.verifyPurchase(userId, bookId);
        assertThat(result).isTrue();
    }

    @Test
    void testAddReview_Duplicate() {
        when(reviewRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.of(testReview));
        
        assertThatThrownBy(() -> reviewService.addReview(testReview))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void testAddReview_NullComment() {
        testReview.setComment(null);
        when(reviewRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.empty());
        
        OrderDTO order = new OrderDTO();
        order.setBookId(bookId);
        order.setOrderStatus("DELIVERED");
        when(orderClient.getOrdersByUserId(userId)).thenReturn(new ApiResponse<>(true, "ok", Collections.singletonList(order)));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review result = reviewService.addReview(testReview);
        assertThat(result.getComment()).isNull();
    }

    @Test
    void testUpdateReview_NullComment() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Review updateRequest = Review.builder().rating(4).comment(null).build();
        Review result = reviewService.updateReview(1L, updateRequest);

        assertThat(result.getComment()).isNull();
    }

    @Test
    void testApproveReview_NotFound() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reviewService.approveReview(1L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testRejectReview_NotFound() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reviewService.rejectReview(1L))
                .isInstanceOf(RuntimeException.class);
    }
}
