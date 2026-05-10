package com.booknest.walletservice.consumer;

import com.booknest.walletservice.dto.OrderEvent;
import com.booknest.walletservice.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class OrderEventConsumerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    private Consumer<OrderEvent> consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = orderEventConsumer.refundConsumer();
    }

    @Test
    void testRefundConsumer_ValidRefundRequest() {
        OrderEvent event = new OrderEvent();
        event.setType("PAYMENT");
        event.setStatus("REFUND_REQUESTED");
        event.setOrderId(1L);
        event.setUserId(2L);
        event.setAmount(100.0);

        consumer.accept(event);

        verify(walletService, times(1)).addMoneyToWallet(2L, 100.0);
    }

    @Test
    void testRefundConsumer_InvalidType() {
        OrderEvent event = new OrderEvent();
        event.setType("ORDER");
        event.setStatus("REFUND_REQUESTED");

        consumer.accept(event);

        verify(walletService, never()).addMoneyToWallet(anyLong(), anyDouble());
    }

    @Test
    void testRefundConsumer_InvalidStatus() {
        OrderEvent event = new OrderEvent();
        event.setType("PAYMENT");
        event.setStatus("COMPLETED");

        consumer.accept(event);

        verify(walletService, never()).addMoneyToWallet(anyLong(), anyDouble());
    }

    @Test
    void testRefundConsumer_NullAmount() {
        OrderEvent event = new OrderEvent();
        event.setType("PAYMENT");
        event.setStatus("REFUND_REQUESTED");
        event.setOrderId(1L);
        event.setUserId(2L);
        event.setAmount(null);

        consumer.accept(event);

        verify(walletService, never()).addMoneyToWallet(anyLong(), anyDouble());
    }

    @Test
    void testRefundConsumer_ZeroAmount() {
        OrderEvent event = new OrderEvent();
        event.setType("PAYMENT");
        event.setStatus("REFUND_REQUESTED");
        event.setOrderId(1L);
        event.setUserId(2L);
        event.setAmount(0.0);

        consumer.accept(event);

        verify(walletService, never()).addMoneyToWallet(anyLong(), anyDouble());
    }

    @Test
    void testRefundConsumer_ExceptionThrown() {
        OrderEvent event = new OrderEvent();
        event.setType("PAYMENT");
        event.setStatus("REFUND_REQUESTED");
        event.setOrderId(1L);
        event.setUserId(2L);
        event.setAmount(100.0);

        doThrow(new RuntimeException("Database error")).when(walletService).addMoneyToWallet(2L, 100.0);

        assertThrows(RuntimeException.class, () -> consumer.accept(event));

        verify(walletService, times(1)).addMoneyToWallet(2L, 100.0);
    }
}
