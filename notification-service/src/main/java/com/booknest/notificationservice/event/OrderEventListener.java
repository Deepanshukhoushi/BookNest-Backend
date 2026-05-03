package com.booknest.notificationservice.event;

import com.booknest.notificationservice.client.AuthClient;
import com.booknest.notificationservice.client.BookClient;
import com.booknest.notificationservice.client.OrderClient;
import com.booknest.notificationservice.dto.BookDTO;
import com.booknest.notificationservice.dto.OrderDTO;
import com.booknest.notificationservice.dto.OrderEvent;
import com.booknest.notificationservice.dto.UserDTO;
import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.entity.NotificationType;
import com.booknest.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.List;

/**
 * Component that listens for asynchronous order events from the message broker.
 * Orchestrates the creation of in-app notifications and the dispatch of email alerts.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final NotificationService notificationService;
    private final AuthClient authClient;
    private final OrderClient orderClient;
    private final BookClient bookClient;

    // Defines the processing logic for incoming order-related events
    @Bean
    public Consumer<OrderEvent> orderProcessor() {
        return event -> {
            log.info("Received Order Event: {}", event);
            
            try {
                // 1. Create and Save Notification
                Notification notification = new Notification();
                notification.setUserId(event.getUserId());
                notification.setMessage(event.getMessage());
                notification.setType(NotificationType.valueOf(event.getType()));
                notificationService.sendNotification(notification);
                
                // 2. Fetch User Email and Send Email Alert
                UserDTO user = authClient.getUserById(event.getUserId()).getData();
                if (user != null && user.getEmail() != null) {
                    
                    if ("ORDER".equalsIgnoreCase(event.getType()) && "PLACED".equalsIgnoreCase(event.getStatus())) {
                        sendRichOrderEmail(user, event);
                    } else {
                        // Standard text email for other events
                        notificationService.sendEmailAlert(
                            user.getEmail(),
                            "Booknest Update: " + event.getType() + " - " + event.getStatus(),
                            "Hello " + user.getFullName() + ",\n\n" + event.getMessage() + "\n\nBest regards,\nBooknest Team"
                        );
                    }
                }

                if (shouldNotifyAdmins(event)) {
                    notifyAdmins(event);
                }
                
                log.info("Successfully processed notification for orderId={}", event.getOrderId());
            } catch (Exception e) {
                log.error("Error processing order event: {}", e.getMessage());
            }
        };
    }

    private void sendRichOrderEmail(UserDTO user, OrderEvent event) {
        try {
            log.info("Preparing rich order confirmation for orderId={}", event.getOrderId());
            
            // Fetch order details
            OrderDTO order = orderClient.getOrderById(event.getOrderId()).getData();
            if (order == null) {
                log.warn("Could not fetch order details for ID: {}", event.getOrderId());
                return;
            }

            // Fetch book details for the image
            BookDTO book = bookClient.getBookById(order.getBookId()).getData();
            if (book == null) {
                log.warn("Could not fetch book details for ID: {}", order.getBookId());
                // Fallback book object so template doesn't crash
                book = BookDTO.builder()
                        .title(order.getBookName())
                        .author("Unknown")
                        .price(order.getAmountPaid())
                        .build();
            }

            // Prepare template context
            Map<String, Object> variables = new HashMap<>();
            variables.put("userName", user.getFullName());
            variables.put("order", order);
            variables.put("book", book);
            
            // Format expected delivery (Order Date + 5 days)
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
            String expectedDelivery = (order.getOrderDate() != null ? order.getOrderDate() : event.getTimestamp())
                    .plusDays(5).format(formatter);
            variables.put("expectedDelivery", expectedDelivery);

            notificationService.sendHtmlEmail(
                user.getEmail(),
                "Your Booknest Order is Confirmed! (#" + event.getOrderId() + ")",
                "order-confirmation",
                variables
            );
        } catch (Exception e) {
            log.error("Failed to send rich order email: {}", e.getMessage());
        }
    }

    private boolean shouldNotifyAdmins(OrderEvent event) {
        if (event == null) {
            return false;
        }
        if ("ORDER".equalsIgnoreCase(event.getType()) && "PLACED".equalsIgnoreCase(event.getStatus())) {
            return true;
        }
        return "SYSTEM".equalsIgnoreCase(event.getType()) && "LOW_STOCK".equalsIgnoreCase(event.getStatus());
    }

    private void notifyAdmins(OrderEvent event) {
        List<UserDTO> users = authClient.getAllUsers().getData();
        if (users == null) {
            return;
        }

        for (UserDTO admin : users) {
            if (!"ADMIN".equalsIgnoreCase(admin.getRole()) || Boolean.TRUE.equals(admin.getSuspended())) {
                continue;
            }

            Notification adminNotification = new Notification();
            adminNotification.setUserId(admin.getUserId());
            adminNotification.setMessage(event.getMessage());
            adminNotification.setType("LOW_STOCK".equalsIgnoreCase(event.getStatus()) ? NotificationType.SYSTEM : NotificationType.ORDER);
            notificationService.sendNotification(adminNotification);

            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                notificationService.sendEmailAlert(
                        admin.getEmail(),
                        "Booknest Admin Alert: " + event.getStatus(),
                        "Hello " + admin.getFullName() + ",\n\n" + event.getMessage() + "\n\nRegards,\nBooknest Platform");
            }
        }
    }
}
