package com.booknest.notificationservice.controller;

import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NotificationResource {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getByUser(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(notificationService.getByUser(userId));
    }

    @PutMapping("/read/{notificationId}")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long notificationId, HttpServletRequest request) {
        enforceNotificationAccess(notificationId, request);
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    @PutMapping("/readAll/{userId}")
    public ResponseEntity<String> markAllRead(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok("All notifications marked as read");
    }

    @GetMapping("/unreadCount/{userId}")
    public ResponseEntity<Long> getUnreadCount(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<String> deleteNotification(@PathVariable Long notificationId, HttpServletRequest request) {
        enforceNotificationAccess(notificationId, request);
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok("Notification deleted successfully");
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<String> deleteAllByUser(@PathVariable Long userId, HttpServletRequest request) {
        enforceUserAccess(userId, request);
        notificationService.deleteAllByUser(userId);
        return ResponseEntity.ok("All notifications deleted successfully");
    }

    @GetMapping("/all")
    public ResponseEntity<List<Notification>> getAllNotifications(HttpServletRequest request) {
        enforceAdminAccess(request);
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    @PostMapping("/send")
    public ResponseEntity<Notification> sendNotification(@RequestBody com.booknest.notificationservice.dto.NotificationRequest requestPayload, HttpServletRequest request) {
        enforceAdminAccess(request);
        Notification notification = Notification.builder()
                .userId(requestPayload.getUserId())
                .type(requestPayload.getType())
                .message(requestPayload.getMessage())
                .build();
        return ResponseEntity.ok(notificationService.sendNotification(notification));
    }

    @PostMapping("/email")
    public ResponseEntity<String> sendEmail(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        enforceAdminAccess(request);
        notificationService.sendEmailAlert(
                payload.get("email"),
                payload.get("subject"),
                payload.get("message")
        );
        return ResponseEntity.ok("Email dispatch triggered");
    }

    private void enforceUserAccess(Long targetUserId, HttpServletRequest request) {
        if (targetUserId == null || request == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Long authenticatedUserId = resolveUserId(request);
        if (authenticatedUserId == null) {
            return;
        }

        if (!authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for requested user resource");
        }
    }

    private void enforceNotificationAccess(Long notificationId, HttpServletRequest request) {
        if (notificationId == null || request == null) {
            return;
        }

        String roleHeader = resolveRole(request);
        if (isAdmin(roleHeader)) {
            return;
        }

        Notification notification = notificationService.getById(notificationId);
        enforceUserAccess(notification.getUserId(), request);
    }

    private void enforceAdminAccess(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        if (!isAdmin(resolveRole(request))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
    }

    private String resolveRole(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedRole");
        if (attribute instanceof String role && !role.isBlank()) {
            return role;
        }
        return request.getHeader("X-Auth-Role");
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("authenticatedUserId");
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Integer userId) {
            return userId.longValue();
        }

        String userIdHeader = request.getHeader("X-Auth-UserId");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user context");
        }
    }

    private boolean isAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return false;
        }
        String normalized = roleHeader.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return "ADMIN".equals(normalized);
    }
}
