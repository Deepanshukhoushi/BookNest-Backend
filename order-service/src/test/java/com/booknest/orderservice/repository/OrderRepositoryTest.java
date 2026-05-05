package com.booknest.orderservice.repository;

import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
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
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testSaveAndFindByUserId() {
        Order order = Order.builder()
                .userId(1L)
                .bookId(101L)
                .bookName("Mock Book")
                .quantity(1)
                .amountPaid(29.99)
                .modeOfPayment("WALLET")
                .orderStatus(OrderStatus.PLACED)
                .orderDate(java.time.LocalDateTime.now())
                .build();
        orderRepository.save(order);

        List<Order> orders = orderRepository.findByUserId(1L);
        assertThat(orders).isNotEmpty();
        assertThat(orders.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void testFindByRazorpayOrderId() {
        Order order = Order.builder()
                .userId(1L)
                .bookId(102L)
                .bookName("Another Book")
                .quantity(1)
                .amountPaid(19.99)
                .modeOfPayment("RAZORPAY")
                .razorpayOrderId("rzp_order_1")
                .orderStatus(OrderStatus.PLACED)
                .orderDate(java.time.LocalDateTime.now())
                .build();
        orderRepository.save(order);

        List<Order> orders = orderRepository.findByRazorpayOrderId("rzp_order_1");
        assertThat(orders).isNotEmpty();
        assertThat(orders.get(0).getRazorpayOrderId()).isEqualTo("rzp_order_1");
    }
}
