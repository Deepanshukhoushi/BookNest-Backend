package com.booknest.notificationservice.repository;

import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void testFindByUserIdOrderByCreatedAtDesc() {
        Notification n1 = Notification.builder().userId(1L).type(NotificationType.ORDER).message("M1").build();
        Notification n2 = Notification.builder().userId(1L).type(NotificationType.SYSTEM).message("M2").build();
        notificationRepository.save(n1);
        notificationRepository.save(n2);

        List<Notification> results = notificationRepository.findByUserIdOrderByCreatedAtDesc(1L);
        assertThat(results).hasSize(2);
    }

    @Test
    void testCountByUserIdAndIsRead() {
        Notification n1 = Notification.builder().userId(1L).type(NotificationType.ORDER).message("M1").isRead(false).build();
        Notification n2 = Notification.builder().userId(1L).type(NotificationType.SYSTEM).message("M2").isRead(true).build();
        notificationRepository.save(n1);
        notificationRepository.save(n2);

        long unread = notificationRepository.countByUserIdAndIsRead(1L, false);
        assertThat(unread).isEqualTo(1);
    }

    // ── Additional repository coverage ────────────────────────────────────────

    @Test
    void testFindByUserIdAndIsReadOrderByCreatedAtDesc_UnreadOnly() {
        Notification unread = Notification.builder()
                .userId(2L).type(NotificationType.ORDER).message("Unread").isRead(false).build();
        Notification read = Notification.builder()
                .userId(2L).type(NotificationType.SYSTEM).message("Read").isRead(true).build();
        notificationRepository.save(unread);
        notificationRepository.save(read);

        var results = notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(2L, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).isEqualTo("Unread");
    }

    @Test
    void testDeleteByUserId() {
        Notification n = Notification.builder()
                .userId(3L).type(NotificationType.ORDER).message("Del me").isRead(false).build();
        notificationRepository.save(n);

        notificationRepository.deleteByUserId(3L);

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(3L)).isEmpty();
    }

    @Test
    void testFindByUserId_NoNotifications_ReturnsEmpty() {
        var results = notificationRepository.findByUserIdOrderByCreatedAtDesc(9999L);
        assertThat(results).isEmpty();
    }

    @Test
    void testCountByUserIdAndIsRead_Zero() {
        long count = notificationRepository.countByUserIdAndIsRead(8888L, false);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testFindAll() {
        notificationRepository.save(Notification.builder()
                .userId(10L).type(NotificationType.SYSTEM).message("A").isRead(false).build());
        notificationRepository.save(Notification.builder()
                .userId(11L).type(NotificationType.ORDER).message("B").isRead(true).build());

        assertThat(notificationRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}

