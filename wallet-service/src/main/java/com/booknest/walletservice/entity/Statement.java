package com.booknest.walletservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "statements")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a historical transaction record within a wallet.
 * Captures the amount, type (Debit/Credit), and associated order details.
 */
@Builder
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType transactionType;

    @Column(nullable = false, updatable = false)
    private Double amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateTime;

    @Column(updatable = false)
    private Long orderId;

    @Column(updatable = false)
    private String transactionRemarks;

    @Column(name = "payment_gateway", updatable = false)
    private String paymentGateway;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", updatable = false)
    @JsonIgnore
    private Wallet wallet;
}
