package com.booknest.walletservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "E-Wallet", description = "Maps to E-Wallet terminology in domain, representing internal funds.")
@Table(name = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a user's internal digital wallet.
 * Maintains the current financial balance and tracks a collection of transaction statements.
 */
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    @Min(0)
    private Double currentBalance = 0.0;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Statement> statements = new ArrayList<>();
}
