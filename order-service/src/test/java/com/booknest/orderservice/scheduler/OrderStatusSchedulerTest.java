package com.booknest.orderservice.scheduler;

import com.booknest.orderservice.entity.Order;
import com.booknest.orderservice.enums.OrderStatus;
import com.booknest.orderservice.repository.OrderRepository;
import com.booknest.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderStatusScheduler scheduler;

    @Test
    void autoProgressOrders_ProgressesEligibleOrdersAcrossStages() {
        LocalDateTime oldDate = LocalDateTime.now().minusMinutes(5);
        Order placed = Order.builder().orderId(1L).orderStatus(OrderStatus.PLACED).orderDate(oldDate).build();
        Order confirmed = Order.builder().orderId(2L).orderStatus(OrderStatus.CONFIRMED).orderDate(oldDate).build();
        Order paid = Order.builder().orderId(3L).orderStatus(OrderStatus.PAID).orderDate(oldDate).build();
        Order shipped = Order.builder().orderId(4L).orderStatus(OrderStatus.SHIPPED).orderDate(oldDate).build();
        Order outForDelivery = Order.builder().orderId(5L).orderStatus(OrderStatus.OUT_FOR_DELIVERY).orderDate(oldDate).build();
        Order recent = Order.builder().orderId(6L).orderStatus(OrderStatus.PLACED).orderDate(LocalDateTime.now()).build();

        when(orderRepository.findAll()).thenReturn(List.of(placed, confirmed, paid, shipped, outForDelivery, recent));

        scheduler.autoProgressOrders();

        verify(orderService).changeStatus(1L, OrderStatus.CONFIRMED);
        verify(orderService).changeStatus(2L, OrderStatus.SHIPPED);
        verify(orderService).changeStatus(3L, OrderStatus.SHIPPED);
        verify(orderService).changeStatus(4L, OrderStatus.OUT_FOR_DELIVERY);
        verify(orderService).changeStatus(5L, OrderStatus.DELIVERED);
        verify(orderService, times(5)).changeStatus(anyLong(), any());
    }

    @Test
    void autoProgressOrders_ContinuesWhenAStatusChangeFails() {
        LocalDateTime oldDate = LocalDateTime.now().minusMinutes(5);
        Order first = Order.builder().orderId(10L).orderStatus(OrderStatus.PLACED).orderDate(oldDate).build();
        Order second = Order.builder().orderId(11L).orderStatus(OrderStatus.PLACED).orderDate(oldDate).build();

        when(orderRepository.findAll()).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(orderService).changeStatus(10L, OrderStatus.CONFIRMED);

        scheduler.autoProgressOrders();

        verify(orderService).changeStatus(10L, OrderStatus.CONFIRMED);
        verify(orderService).changeStatus(11L, OrderStatus.CONFIRMED);
    }
}
