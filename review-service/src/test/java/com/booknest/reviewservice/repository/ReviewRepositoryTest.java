package com.booknest.reviewservice.repository;

import com.booknest.reviewservice.entity.Review;
import com.booknest.reviewservice.entity.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void testSaveAndFindByBookIdAndStatus() {
        Review review = Review.builder()
                .bookId(101L)
                .userId(1L)
                .status(ReviewStatus.APPROVED)
                .rating(5)
                .comment("Excellent book!")
                .verified(true)
                .build();
        reviewRepository.save(review);

        List<Review> reviews = reviewRepository.findByBookIdAndStatus(101L, ReviewStatus.APPROVED);
        assertThat(reviews).isNotEmpty();
        assertThat(reviews.get(0).getBookId()).isEqualTo(101L);
    }

    @Test
    void testFindAvgRatingByBookId() {
        Review r1 = Review.builder().bookId(102L).userId(1L).status(ReviewStatus.APPROVED).rating(4).comment("Good")
                .verified(true).build();
        Review r2 = Review.builder().bookId(102L).userId(2L).status(ReviewStatus.APPROVED).rating(5).comment("Great")
                .verified(true).build();
        reviewRepository.save(r1);
        reviewRepository.save(r2);

        Double avg = reviewRepository.findAvgRatingByBookId(102L);
        assertThat(avg).isEqualTo(4.5);
    }

    @Test
    void testFindByUserId() {
        Review review = Review.builder().bookId(103L).userId(1L).status(ReviewStatus.APPROVED).rating(5).comment("Test").verified(true).build();
        reviewRepository.save(review);
        List<Review> reviews = reviewRepository.findByUserId(1L);
        assertThat(reviews).isNotEmpty();
    }

    @Test
    void testFindByStatus() {
        Review review = Review.builder().bookId(104L).userId(1L).status(ReviewStatus.PENDING).rating(3).comment("Test").verified(true).build();
        reviewRepository.save(review);
        List<Review> reviews = reviewRepository.findByStatus(ReviewStatus.PENDING);
        assertThat(reviews).isNotEmpty();
    }

    @Test
    void testCountByStatus() {
        Review review = Review.builder().bookId(105L).userId(1L).status(ReviewStatus.APPROVED).rating(4).comment("Test").verified(true).build();
        reviewRepository.save(review);
        long count = reviewRepository.countByStatus(ReviewStatus.APPROVED);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testFindByBookIdAndUserId() {
        Review review = Review.builder().bookId(106L).userId(1L).status(ReviewStatus.APPROVED).rating(5).comment("Test").verified(true).build();
        reviewRepository.save(review);
        assertThat(reviewRepository.findByBookIdAndUserId(106L, 1L)).isPresent();
    }
}
