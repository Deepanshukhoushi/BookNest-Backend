package com.booknest.orderservice.repository;

import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
    Optional<Order> findFirstByOrderByOrderIdDesc();
    List<Order> findByOrderStatus(OrderStatus status);
    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);
    List<Order> findByRazorpayOrderId(String razorpayOrderId);

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.orderStatus = :status AND o.orderDate < :cutoff")
    List<Order> findByOrderStatusAndOrderDateBefore(
        @org.springframework.data.repository.query.Param("status") OrderStatus status, 
        @org.springframework.data.repository.query.Param("cutoff") LocalDateTime cutoff);
}
