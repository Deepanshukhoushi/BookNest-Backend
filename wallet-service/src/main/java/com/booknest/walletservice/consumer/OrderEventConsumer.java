package com.booknest.walletservice.consumer;

import com.booknest.walletservice.dto.OrderEvent;
import com.booknest.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final WalletService walletService;

    @Bean
    public Consumer<OrderEvent> refundConsumer() {
        return event -> {
            if ("PAYMENT".equals(event.getType()) && "REFUND_REQUESTED".equals(event.getStatus())) {
                log.info("Received refund request for orderId: {}, userId: {}, amount: {}", 
                        event.getOrderId(), event.getUserId(), event.getAmount());
                
                try {
                    if (event.getAmount() != null && event.getAmount() > 0) {
                        walletService.addMoneyToWallet(event.getUserId(), event.getAmount());
                        log.info("Refund successful for orderId: {}", event.getOrderId());
                    } else {
                        log.warn("Refund failed: Invalid amount {} for orderId: {}", event.getAmount(), event.getOrderId());
                    }
                } catch (Exception e) {
                    log.error("Error processing refund for orderId: {}. Sending to DLQ. Error: {}", 
                            event.getOrderId(), e.getMessage());
                    throw e; // Throwing exception triggers retry/DLQ logic
                }
            }
        };
    }
}
