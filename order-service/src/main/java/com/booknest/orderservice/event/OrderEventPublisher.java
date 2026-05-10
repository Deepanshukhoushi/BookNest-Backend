package com.booknest.orderservice.event;

import com.booknest.orderservice.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final StreamBridge streamBridge;

    public void publishOrderEvent(Long orderId, Long userId, String type, String status, String message, Double amount) {
        OrderEvent event = OrderEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .type(type)
                .status(status)
                .message(message)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .build();
        
        log.info("Publishing Order Event: {}", event);
        try {
            boolean accepted = streamBridge.send("order-out-0", event);
            if (!accepted) {
                log.warn("Order event was not accepted by the outbound binding: {}", event);
            }
        } catch (Exception ex) {
            log.warn("Order event publish skipped because the messaging broker is unavailable: {}", ex.getMessage());
        }
    }
}
