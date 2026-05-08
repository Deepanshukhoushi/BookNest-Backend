package com.booknest.notificationservice.event;

import com.booknest.notificationservice.client.AuthClient;
import com.booknest.notificationservice.client.OrderClient;
import com.booknest.notificationservice.dto.*;
import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.service.MailService;
import com.booknest.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private MailService mailService;

    @Mock
    private AuthClient authClient;

    @Mock
    private OrderClient orderClient;



    @InjectMocks
    private OrderEventListener orderEventListener;

    private Consumer<OrderEvent> orderProcessor;

    @BeforeEach
    void setUp() {
        orderProcessor = orderEventListener.orderProcessor();
    }

    @Test
    void testOrderProcessor_StandardEvent() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .orderId(101L)
                .type("ORDER")
                .status("SHIPPED")
                .message("Your order has been shipped")
                .build();

        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setEmail("user@test.com");
        user.setFullName("Test User");

        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", user));

        orderProcessor.accept(event);

        verify(notificationService).sendNotification(any(Notification.class));
        verify(notificationService).sendHtmlEmail(eq("user@test.com"), contains("SHIPPED"), eq("status-update-email"), anyMap());
    }

    @Test
    void testOrderProcessor_RichOrderPlacedEvent() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .orderId(101L)
                .type("ORDER")
                .status("PLACED")
                .message("Order placed successfully")
                .timestamp(LocalDateTime.now())
                .build();

        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setEmail("user@test.com");
        user.setFullName("Test User");

        OrderDTO order = OrderDTO.builder()
                .orderId(101L)
                .bookId(501L)
                .bookName("Java Guide")
                .amountPaid(49.99)
                .orderDate(LocalDateTime.now())
                .build();



        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", user));
        when(orderClient.getOrderById(101L)).thenReturn(new ApiResponse<>(true, "Success", order));

        orderProcessor.accept(event);

        verify(notificationService).sendNotification(any(Notification.class));
        verify(mailService).sendOrderConfirmation(eq("user@test.com"), contains("Order Confirmed"), anyMap());
    }

    @Test
    void testOrderProcessor_AdminNotification_LowStock() {
        OrderEvent event = OrderEvent.builder()
                .type("SYSTEM")
                .status("LOW_STOCK")
                .message("Stock is low for Book X")
                .build();

        UserDTO admin = new UserDTO();
        admin.setUserId(99L);
        admin.setRole("ADMIN");
        admin.setEmail("admin@test.com");
        admin.setFullName("Admin User");
        admin.setSuspended(false);

        when(authClient.getUserById(any())).thenReturn(new ApiResponse<>(true, "Success", null));
        when(authClient.getAllUsers()).thenReturn(new ApiResponse<>(true, "Success", Collections.singletonList(admin)));
        // User not found for specific event userId (which is null here), authClient.getUserById might return error
        // But the code fetches user email for ANY event if event.getUserId() is not null.
        // In this case event.getUserId() is null, so it skips step 2 and goes to notifyAdmins.

        orderProcessor.accept(event);

        org.mockito.ArgumentCaptor<Notification> captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, atLeastOnce()).sendNotification(captor.capture());
        
        boolean foundAdminNotif = captor.getAllValues().stream()
                .anyMatch(n -> Long.valueOf(99).equals(n.getUserId()));
        assertTrue(foundAdminNotif, "Admin notification for userId 99 not found");
        
        verify(notificationService).sendHtmlEmail(eq("admin@test.com"), contains("Admin Alert"), eq("admin-alert-email"), anyMap());
    }

    @Test
    void testOrderProcessor_UserNotFound() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .type("ORDER")
                .status("SHIPPED")
                .build();
        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", null));

        orderProcessor.accept(event);

        verify(notificationService).sendNotification(any(Notification.class));
        verify(notificationService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void testOrderProcessor_RichOrderPlaced_OrderNotFound() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .orderId(101L)
                .type("ORDER")
                .status("PLACED")
                .build();
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setEmail("user@test.com");
        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", user));
        when(orderClient.getOrderById(101L)).thenReturn(new ApiResponse<>(true, "Success", null));
        when(authClient.getAllUsers()).thenReturn(new ApiResponse<>(true, "Success", Collections.emptyList()));

        orderProcessor.accept(event);

        verify(notificationService).sendNotification(any(Notification.class));
        verify(mailService, never()).sendOrderConfirmation(anyString(), anyString(), anyMap());
    }

    @Test
    void testOrderProcessor_RichOrderPlaced_BookNotFound_UsesFallback() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .orderId(101L)
                .type("ORDER")
                .status("PLACED")
                .timestamp(LocalDateTime.now())
                .build();
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setEmail("user@test.com");
        
        OrderDTO order = OrderDTO.builder()
                .orderId(101L)
                .bookId(501L)
                .bookName("Java Guide")
                .amountPaid(49.99)
                .orderDate(LocalDateTime.now())
                .build();

        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", user));
        when(orderClient.getOrderById(101L)).thenReturn(new ApiResponse<>(true, "Success", order));
        when(authClient.getAllUsers()).thenReturn(new ApiResponse<>(true, "Success", Collections.emptyList()));

        orderProcessor.accept(event);

        verify(mailService).sendOrderConfirmation(eq("user@test.com"), anyString(), anyMap());
    }

    @Test
    void testOrderProcessor_AdminNotify_UsersNull() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .type("SYSTEM")
                .status("LOW_STOCK")
                .build();

        when(authClient.getUserById(1L)).thenReturn(new ApiResponse<>(true, "Success", null));
        when(authClient.getAllUsers()).thenReturn(new ApiResponse<>(true, "Success", null));

        orderProcessor.accept(event);

        verify(notificationService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());
    }
    @Test
    void testOrderProcessor_HandlesClientFailure() {
        OrderEvent event = OrderEvent.builder()
                .userId(1L)
                .type("ORDER")
                .status("PLACED")
                .build();

        when(authClient.getUserById(1L)).thenThrow(new RuntimeException("Auth Service Down"));

        // Should not throw exception
        orderProcessor.accept(event);

        verify(notificationService).sendNotification(any(Notification.class));
        // Other steps should be skipped due to exception
        verify(notificationService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());
    }
}

