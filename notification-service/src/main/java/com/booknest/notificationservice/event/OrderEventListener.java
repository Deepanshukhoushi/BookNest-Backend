package com.booknest.notificationservice.event;

import com.booknest.notificationservice.client.AuthClient;
import com.booknest.notificationservice.client.OrderClient;
import com.booknest.notificationservice.dto.OrderDTO;
import com.booknest.notificationservice.dto.OrderEvent;
import com.booknest.notificationservice.dto.UserDTO;
import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.entity.NotificationType;
import com.booknest.notificationservice.service.MailService;
import com.booknest.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.List;

/**
 * Component that listens for asynchronous order events from the message broker.
 * Orchestrates the creation of in-app notifications and the dispatch of email
 * alerts.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuthClient authClient;
    private final OrderClient orderClient;

    // Defines the processing logic for incoming order-related events
    @Bean
    public Consumer<OrderEvent> orderProcessor() {
        return event -> {
            log.info("Received Order Event: {}", event);

            try {
                // 1. Create and Save In-App Notification
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
                    } else if ("ORDER".equalsIgnoreCase(event.getType())) {
                        // HTML email for status updates (CONFIRMED, SHIPPED, etc.)
                        Map<String, Object> vars = new HashMap<>();
                        vars.put("customerName", user.getFullName());
                        vars.put("orderID", event.getOrderId());
                        vars.put("status", event.getStatus());
                        vars.put("message", event.getMessage());

                        notificationService.sendHtmlEmail(
                                user.getEmail(),
                                "Order Update: #" + event.getOrderId() + " is " + event.getStatus(),
                                "status-update-email",
                                vars);
                    } else {
                        // Fallback for other user events
                        notificationService.sendEmailAlert(
                                user.getEmail(),
                                "Booknest Update: " + event.getType(),
                                event.getMessage());
                    }
                }

                // 3. Handle Admin Alerts
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

            OrderDTO order = orderClient.getOrderById(event.getOrderId()).getData();
            if (order == null)
                return;

            Map<String, Object> variables = new HashMap<>();
            variables.put("customerName", user.getFullName());
            variables.put("orderID", event.getOrderId());
            variables.put("amount", order.getAmountPaid());

            mailService.sendOrderConfirmation(
                    user.getEmail(),
                    "Order Confirmed: #" + event.getOrderId() + " - BookNest",
                    variables);
        } catch (Exception e) {
            log.error("Failed to send rich order email: {}", e.getMessage());
        }
    }

    private boolean shouldNotifyAdmins(OrderEvent event) {
        if (event == null)
            return false;
        return ("ORDER".equalsIgnoreCase(event.getType()) && "PLACED".equalsIgnoreCase(event.getStatus())) ||
                ("SYSTEM".equalsIgnoreCase(event.getType()) && "LOW_STOCK".equalsIgnoreCase(event.getStatus()));
    }

    private void notifyAdmins(OrderEvent event) {
        List<UserDTO> users = authClient.getAllUsers().getData();
        if (users == null)
            return;

        for (UserDTO admin : users) {
            if (!"ADMIN".equalsIgnoreCase(admin.getRole()) || Boolean.TRUE.equals(admin.getSuspended())) {
                continue;
            }

            // In-app notification
            Notification adminNotification = new Notification();
            adminNotification.setUserId(admin.getUserId());
            adminNotification.setMessage(event.getMessage());
            adminNotification.setType(
                    "LOW_STOCK".equalsIgnoreCase(event.getStatus()) ? NotificationType.SYSTEM : NotificationType.ORDER);
            notificationService.sendNotification(adminNotification);

            // HTML Email Alert
            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("adminName", admin.getFullName());
                vars.put("alertType",
                        "LOW_STOCK".equalsIgnoreCase(event.getStatus()) ? "INVENTORY ALERT" : "NEW ORDER ALERT");
                vars.put("subject",
                        "LOW_STOCK".equalsIgnoreCase(event.getStatus()) ? "Low Stock Warning" : "New Order Received");
                vars.put("message", event.getMessage());

                notificationService.sendHtmlEmail(
                        admin.getEmail(),
                        "Booknest Admin Alert: " + event.getStatus(),
                        "admin-alert-email",
                        vars);
            }
        }
    }
}
