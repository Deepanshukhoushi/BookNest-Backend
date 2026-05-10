package com.booknest.orderservice.scheduler;

import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.repository.OrderRepository;
import com.booknest.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background scheduler responsible for simulating the order fulfillment
 * lifecycle.
 * Periodically advances orders from 'Placed' through to 'Delivered' for
 * demonstration purposes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Automatically progresses orders through the lifecycle every 60 seconds.
     * PLACED -> CONFIRMED -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED
     */
    @Scheduled(fixedDelay = 60000)
    public void autoProgressOrders() {
        log.info("Starting automated order status progression...");
        // Use a 2-minute cutoff to give users time to see initial states
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);

        // 1. CONFIRM placed orders
        progressOrders(OrderStatus.PLACED, OrderStatus.CONFIRMED, cutoff);

        // 2. SHIP confirmed/paid orders
        progressOrders(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, cutoff);
        progressOrders(OrderStatus.PAID, OrderStatus.SHIPPED, cutoff);

        // 3. OUT FOR DELIVERY
        progressOrders(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, cutoff);

        // 4. DELIVER
        progressOrders(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, cutoff);

        log.info("Automated progression completed.");
    }

    /**
     * Identifies orders in a specific state and migrates them to the next logical state.
     * Uses optimized repository queries instead of fetching all orders.
     */
    private void progressOrders(OrderStatus current, OrderStatus next, LocalDateTime cutoff) {
        List<Order> orders = orderRepository.findByOrderStatusAndOrderDateBefore(current, cutoff);

        for (Order order : orders) {
            try {
                log.info("Auto-progressing Order #{} from {} to {}", order.getOrderId(), current, next);
                orderService.changeStatus(order.getOrderId(), next);
            } catch (Exception e) {
                log.error("Failed to auto-progress order #{}: {}", order.getOrderId(), e.getMessage());
            }
        }
    }
}
