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
import static org.mockito.ArgumentMatchers.eq;
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

        // Stub individual repository calls used in the scheduler
        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.PLACED), any())).thenReturn(List.of(placed));
        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.CONFIRMED), any())).thenReturn(List.of(confirmed));
        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.PAID), any())).thenReturn(List.of(paid));
        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.SHIPPED), any())).thenReturn(List.of(shipped));
        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.OUT_FOR_DELIVERY), any())).thenReturn(List.of(outForDelivery));

        scheduler.autoProgressOrders();

        verify(orderService).changeStatus(1L, OrderStatus.CONFIRMED);
        verify(orderService).changeStatus(2L, OrderStatus.SHIPPED);
        verify(orderService).changeStatus(3L, OrderStatus.SHIPPED);
        verify(orderService).changeStatus(4L, OrderStatus.OUT_FOR_DELIVERY);
        verify(orderService).changeStatus(5L, OrderStatus.DELIVERED);
    }

    @Test
    void autoProgressOrders_ContinuesWhenAStatusChangeFails() {
        LocalDateTime oldDate = LocalDateTime.now().minusMinutes(5);
        Order first = Order.builder().orderId(10L).orderStatus(OrderStatus.PLACED).orderDate(oldDate).build();
        Order second = Order.builder().orderId(11L).orderStatus(OrderStatus.PLACED).orderDate(oldDate).build();

        when(orderRepository.findByOrderStatusAndOrderDateBefore(eq(OrderStatus.PLACED), any())).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(orderService).changeStatus(10L, OrderStatus.CONFIRMED);

        scheduler.autoProgressOrders();

        verify(orderService).changeStatus(10L, OrderStatus.CONFIRMED);
        verify(orderService).changeStatus(11L, OrderStatus.CONFIRMED);
    }

    @Test
    void autoProgressOrders_DoesNotProgressRecentOrders() {
        // Mocking empty lists for all status checks as recent orders wouldn't match the 'before cutoff' query
        when(orderRepository.findByOrderStatusAndOrderDateBefore(any(), any())).thenReturn(List.of());

        scheduler.autoProgressOrders();
        verify(orderService, times(0)).changeStatus(anyLong(), any());
    }
}
