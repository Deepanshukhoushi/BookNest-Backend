package com.booknest.notificationservice.service;

import com.booknest.notificationservice.entity.Notification;

import java.util.List;

public interface NotificationService {
    Notification sendNotification(Notification notification);
    Notification markAsRead(Long notificationId);
    Notification getById(Long notificationId);
    void markAllRead(Long userId);
    List<Notification> getByUser(Long userId);
    long getUnreadCount(Long userId);
    void deleteNotification(Long notificationId);
    void deleteAllByUser(Long userId);
    void sendEmailAlert(String email, String subject, String message);

    void sendHtmlEmail(String to, String subject, String templateName, java.util.Map<String, Object> variables);

    List<Notification> getAllNotifications();
}
