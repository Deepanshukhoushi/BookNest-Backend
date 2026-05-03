package com.booknest.reviewservice.controller;

import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import com.booknest.reviewservice.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class ReviewResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private com.booknest.reviewservice.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.booknest.reviewservice.security.JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testAddReview() throws Exception {
        Review review = Review.builder().bookId(101L).userId(1L).rating(5).comment("Excellent book!").build();
        when(reviewService.addReview(any())).thenReturn(review);

        mockMvc.perform(post("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetByBook() throws Exception {
        when(reviewService.getByBook(101L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reviews/book/101"))
                .andExpect(status().isOk());
    }

    @Test
    void testApproveReview() throws Exception {
        Review review = Review.builder().reviewId(1L).status(ReviewStatus.APPROVED).build();
        when(reviewService.approveReview(1L)).thenReturn(review);

        mockMvc.perform(put("/api/v1/reviews/1/approve")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void testGetAvgRating() throws Exception {
        when(reviewService.getAvgRating(101L)).thenReturn(4.5);

        mockMvc.perform(get("/api/v1/reviews/avg/101"))
                .andExpect(status().isOk())
                .andExpect(content().string("4.5"));
    }

    @Test
    void testRejectReview() throws Exception {
        Review review = Review.builder().reviewId(1L).status(ReviewStatus.REJECTED).build();
        when(reviewService.rejectReview(1L)).thenReturn(review);

        mockMvc.perform(put("/api/v1/reviews/1/reject")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void testGetByUser() throws Exception {
        when(reviewService.getByUser(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reviews/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteReview() throws Exception {
        Review review = Review.builder().reviewId(1L).userId(1L).build();
        when(reviewService.getReviewById(1L)).thenReturn(review);

        mockMvc.perform(delete("/api/v1/reviews/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateReview() throws Exception {
        Review review = Review.builder().reviewId(1L).userId(1L).comment("Updated comment").bookId(101L).rating(4).build();
        when(reviewService.getReviewById(1L)).thenReturn(review);
        when(reviewService.updateReview(anyLong(), any())).thenReturn(review);

        mockMvc.perform(put("/api/v1/reviews/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(review))
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllReviews() throws Exception {
        when(reviewService.getAllReviews()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reviews/all")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetByStatus() throws Exception {
        when(reviewService.getByStatus(ReviewStatus.PENDING)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reviews/all")
                .param("status", "PENDING")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetStatusCounts() throws Exception {
        when(reviewService.getStatusCounts()).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/v1/reviews/counts")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetByBookAndUserId() throws Exception {
        when(reviewService.getByBookAndUserId(101L, 1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/reviews/book/101/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteReview_Forbidden() throws Exception {
        Review review = Review.builder().reviewId(1L).userId(1L).build();
        when(reviewService.getReviewById(1L)).thenReturn(review);

        // User 2 trying to delete User 1's review
        mockMvc.perform(delete("/api/v1/reviews/1")
                .header("X-Auth-UserId", "2")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}
