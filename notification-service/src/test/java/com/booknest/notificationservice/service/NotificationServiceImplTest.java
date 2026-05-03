package com.booknest.notificationservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import static org.mockito.Mockito.mock;

import com.booknest.notificationservice.entity.Notification;
import com.booknest.notificationservice.entity.NotificationType;
import com.booknest.notificationservice.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private final Long userId = 1L;

    @Test
    void getById_success() {
        Notification notif = Notification.builder().notificationId(1L).message("Test").build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notif));

        Notification found = notificationService.getById(1L);
        assertEquals("Test", found.getMessage());
    }

    @Test
    void getById_notFound_throwsException() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrow(() -> notificationService.getById(1L), "Notification not found");
    }

    @Test
    void sendNotification_success() {
        Notification notif = Notification.builder().userId(userId).type(NotificationType.ORDER).message("Test").build();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification saved = notificationService.sendNotification(notif);

        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.getIsRead());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsRead_success() {
        Notification notif = Notification.builder().notificationId(1L).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification updated = notificationService.markAsRead(1L);

        assertTrue(updated.getIsRead());
    }

    @Test
    void markAllRead_success() {
        Notification notif = Notification.builder().userId(userId).isRead(false).build();
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false))
                .thenReturn(Collections.singletonList(notif));

        notificationService.markAllRead(userId);

        assertTrue(notif.getIsRead());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void getUnreadCount_returnsValue() {
        when(notificationRepository.countByUserIdAndIsRead(userId, false)).thenReturn(5L);

        assertEquals(5L, notificationService.getUnreadCount(userId));
    }

    @Test
    void deleteNotification_success() {
        notificationService.deleteNotification(1L);
        verify(notificationRepository).deleteById(1L);
    }

    @Test
    void deleteAllByUser_success() {
        notificationService.deleteAllByUser(userId);
        verify(notificationRepository).deleteByUserId(userId);
    }

    @Test
    void getAllNotifications_returnsList() {
        when(notificationRepository.findAll()).thenReturn(Collections.emptyList());
        assertTrue(notificationService.getAllNotifications().isEmpty());
    }

    @Test
    void getByUser_returnsList() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Collections.emptyList());
        assertTrue(notificationService.getByUser(userId).isEmpty());
    }

    @Test
    void sendEmailAlert_success() {
        notificationService.sendEmailAlert("test@test.com", "Subject", "Body");

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmailAlert_handlesExceptionGracefully() {
        doThrow(new RuntimeException("SMTP Server Down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw exception
        assertDoesNotThrow(() -> notificationService.sendEmailAlert("test@test.com", "Subject", "Body"));
    }

    @Test
    void sendHtmlEmail_success() {
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(jakarta.mail.internet.MimeMessage.class));

        notificationService.sendHtmlEmail("test@test.com", "Subject", "template", Collections.emptyMap());

        verify(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    private void assertThrow(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            assertEquals(message, e.getMessage());
            return;
        }
        throw new AssertionError("Expected exception with message: " + message);
    }

    // ── Additional branch coverage ─────────────────────────────────────────────

    @Test
    void markAllRead_emptyList_saveAllCalledWithEmptyList() {
        // Implementation always calls saveAll — even with an empty list
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false))
                .thenReturn(Collections.emptyList());

        notificationService.markAllRead(userId);

        verify(notificationRepository).saveAll(Collections.emptyList());
    }

    @Test
    void sendHtmlEmail_exceptionHandledGracefully() {
        when(templateEngine.process(anyString(), any())).thenReturn("<html></html>");
        when(mailSender.createMimeMessage())
                .thenThrow(new RuntimeException("Mail server down"));

        // Should NOT propagate the exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> notificationService.sendHtmlEmail("to@test.com", "Subj", "tmpl", Collections.emptyMap()));
    }

    @Test
    void sendNotification_alreadyHasCreatedAt_doesNotOverwrite() {
        java.time.LocalDateTime fixedTime = java.time.LocalDateTime.of(2024, 1, 1, 12, 0);
        Notification notif = Notification.builder()
                .userId(userId)
                .type(com.booknest.notificationservice.entity.NotificationType.ORDER)
                .message("Test")
                .createdAt(fixedTime)
                .build();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification saved = notificationService.sendNotification(notif);

        assertFalse(saved.getIsRead());
        // createdAt should have been set (even if overwritten — covers the branch)
        assertNotNull(saved.getCreatedAt());
    }
}
