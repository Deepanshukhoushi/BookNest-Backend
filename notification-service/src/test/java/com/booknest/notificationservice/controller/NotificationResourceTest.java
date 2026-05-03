package com.booknest.notificationservice.controller;

import com.booknest.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(NotificationResource.class)
@AutoConfigureMockMvc(addFilters = false)
public class NotificationResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void testGetByUser() throws Exception {
        when(notificationService.getByUser(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/notifications/user/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unreadCount/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void testMarkAllRead() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/readAll/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(content().string("All notifications marked as read"));
    }

    @Test
    void testDeleteNotification() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().string("Notification deleted successfully"));
    }

    @Test
    void testDeleteAllByUser() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/user/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(content().string("All notifications deleted successfully"));
    }

    @Test
    void testGetByUser_Forbidden() throws Exception {
        // User 2 trying to access User 1's notifications
        mockMvc.perform(get("/api/v1/notifications/user/1")
                .header("X-Auth-UserId", "2")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetByUser_AdminBypass() throws Exception {
        // Admin trying to access User 1's notifications - should be allowed
        when(notificationService.getByUser(1L)).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/api/v1/notifications/user/1")
                .header("X-Auth-UserId", "99")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ── Additional endpoint coverage ─────────────────────────────────────────

    @Test
    void testGetAllNotifications_Admin() throws Exception {
        when(notificationService.getAllNotifications()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/notifications/all")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllNotifications_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/all")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSendNotification_Admin() throws Exception {
        com.booknest.notificationservice.entity.Notification notif =
                com.booknest.notificationservice.entity.Notification.builder()
                        .notificationId(1L)
                        .userId(1L)
                        .message("Test")
                        .build();
        when(notificationService.sendNotification(any())).thenReturn(notif);

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Auth-Role", "ADMIN")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"message\":\"Test\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testSendEmail_Admin() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/email")
                .header("X-Auth-Role", "ADMIN")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"subject\":\"Hi\",\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("Email dispatch triggered"));
    }

    @Test
    void testMarkAsRead_Admin() throws Exception {
        com.booknest.notificationservice.entity.Notification notif =
                com.booknest.notificationservice.entity.Notification.builder()
                        .notificationId(1L).userId(1L).isRead(true).build();
        when(notificationService.getById(1L)).thenReturn(notif);
        when(notificationService.markAsRead(1L)).thenReturn(notif);

        mockMvc.perform(put("/api/v1/notifications/read/1")
                .header("X-Auth-UserId", "99")
                .header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void testMarkAsRead_SameUser() throws Exception {
        com.booknest.notificationservice.entity.Notification notif =
                com.booknest.notificationservice.entity.Notification.builder()
                        .notificationId(1L).userId(1L).isRead(true).build();
        when(notificationService.getById(1L)).thenReturn(notif);
        when(notificationService.markAsRead(1L)).thenReturn(notif);

        mockMvc.perform(put("/api/v1/notifications/read/1")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isOk());
    }

    @Test
    void testMarkAllRead_Forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/readAll/2")
                .header("X-Auth-UserId", "1")
                .header("X-Auth-Role", "USER"))
                .andExpect(status().isForbidden());
    }
}

