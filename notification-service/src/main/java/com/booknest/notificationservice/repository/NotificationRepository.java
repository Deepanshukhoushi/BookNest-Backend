package com.booknest.notificationservice.repository;

import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);
    long countByUserIdAndIsRead(Long userId, Boolean isRead);
    List<Notification> findByType(NotificationType type);
    void deleteByUserId(Long userId);
}
