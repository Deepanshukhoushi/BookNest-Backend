package com.booknest.reviewservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a user-submitted book review.
 * Captures the rating, comment, moderation status, and verification status for a specific book.
 *
 * <p>Moderation lifecycle: newly submitted reviews start as {@link ReviewStatus#PENDING} and
 * only become publicly visible once an admin sets them to {@link ReviewStatus#APPROVED}.</p>
 *
 * <p>The {@code status} column defaults to {@code 'APPROVED'} at the database level so that
 * rows written before this column was introduced remain publicly visible without any data migration.</p>
 */
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @Column(nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private Long userId;

    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private Integer rating;

    @NotBlank(message = "Comment cannot be empty")
    @Column(nullable = false, length = 2000)
    private String comment;

    @Column(length = 100)
    private String reviewerName;

    /** Stored as 'review_date' in DB but serialized as 'createdAt' in API responses to align with frontend contract. */
    @org.hibernate.annotations.CreationTimestamp
    @Column(nullable = false, updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime reviewDate;

    @Column(nullable = false)
    private Boolean verified;

    /**
     * Moderation status of this review.
     * Defaults to {@link ReviewStatus#APPROVED} at the DB level (for backward compatibility).
     * Service layer sets it to {@link ReviewStatus#PENDING} on every new submission.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'APPROVED'")
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;
}
