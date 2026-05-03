package com.booknest.orderservice.entity;

import com.booknest.orderservice.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Entity representing a customer order in the system.
 * Captures transaction details, payment status, and a full history of state transitions.
 */
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    @Min(0)
    private Double amountPaid;

    @Column(nullable = false)
    private String modeOfPayment;

    // New field to explicitly store the chosen payment method (COD/WALLET/ONLINE).
    // Kept separate from the legacy `modeOfPayment` to avoid breaking existing reads.
    @Column(name = "payment_method")
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    @Min(1)
    private Integer quantity;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private String bookName;
    
    @Column(name = "rzp_order_id")
    private String razorpayOrderId;
    
    @Column(name = "rzp_payment_id")
    private String razorpayPaymentId;
    
    @Column(name = "rzp_signature")
    private String razorpaySignature;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "orderId")
    @Builder.Default
    private java.util.List<OrderStatusLog> statusHistory = new java.util.ArrayList<>();
}
