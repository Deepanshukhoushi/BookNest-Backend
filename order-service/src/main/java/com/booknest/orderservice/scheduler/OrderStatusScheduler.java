package com.booknest.orderservice.scheduler;

import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.repository.OrderRepository;
import com.booknest.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background scheduler responsible for simulating the order fulfillment
 * lifecycle.
 * Periodically advances orders from 'Placed' through to 'Delivered' for
 * demonstration purposes.
 */
// @Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Automatically progresses orders through the lifecycle every 60 seconds.
     * PLACED -> CONFIRMED -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED
     */
    // Periodically triggers the status progression for all eligible orders
    // @Scheduled(fixedDelay = 60000)
    public void autoProgressOrders() {
        log.info("Starting automated order status progression...");

        // 1. CONFIRM placed orders
        progressOrders(OrderStatus.PLACED, OrderStatus.CONFIRMED);

        // 2. SHIP confirmed/paid orders
        progressOrders(OrderStatus.CONFIRMED, OrderStatus.SHIPPED);
        progressOrders(OrderStatus.PAID, OrderStatus.SHIPPED);

        // 3. OUT FOR DELIVERY
        progressOrders(OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY);

        // 4. DELIVER
        progressOrders(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

        log.info("Automated progression completed.");
    }

    // Identifies orders in a specific state and migrates them to the next logical
    // state
    private void progressOrders(OrderStatus current, OrderStatus next) {
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getOrderStatus() == current)
                .filter(o -> o.getOrderDate().isBefore(LocalDateTime.now().minusMinutes(1)))
                .toList();

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
