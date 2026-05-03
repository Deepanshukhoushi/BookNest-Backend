package com.booknest.notificationservice.dto;

import com.booknest.notificationservice.entity.NotificationType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for creating new notifications.
 * Used to avoid mass assignment vulnerabilities when receiving
 * notification payload from the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private Long userId;
    private NotificationType type;
    private String message;
}
