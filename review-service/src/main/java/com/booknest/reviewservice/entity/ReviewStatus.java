package com.booknest.reviewservice.entity;

/**
 * Represents the moderation lifecycle of a user-submitted book review.
 *
 * <ul>
 *   <li>{@code PENDING}  – newly submitted; awaiting admin review.</li>
 *   <li>{@code APPROVED} – approved by admin; visible to the public.</li>
 *   <li>{@code REJECTED} – rejected by admin; hidden from the public.</li>
 * </ul>
 *
 * Existing rows in the database that pre-date this column are handled by the
 * {@code DEFAULT 'APPROVED'} clause on the column definition so they remain
 * visible without any manual migration.
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
