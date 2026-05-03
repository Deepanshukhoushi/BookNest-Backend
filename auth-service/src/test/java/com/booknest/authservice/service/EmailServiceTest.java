package com.booknest.authservice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendOtpEmail_SendsExpectedPlainTextMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendOtpEmail("reader@example.com", "123456");

        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("reader@example.com");
        assertThat(message.getSubject()).isEqualTo("Booknest Library: Access Recovery Code");
        assertThat(message.getText()).contains("123456");
    }

    @Test
    void sendWelcomeEmail_SendsHtmlEmail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(templateEngine.process(eq("welcome-email"), any())).thenReturn("<h1>Hello</h1>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendWelcomeEmail("reader@example.com", "Reader");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_SwallowsTemplateFailures() {
        doThrow(new RuntimeException("template failed")).when(templateEngine).process(eq("welcome-email"), any());

        emailService.sendWelcomeEmail("reader@example.com", "Reader");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
