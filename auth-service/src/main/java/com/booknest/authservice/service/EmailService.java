package com.booknest.authservice.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for sending emails, such as OTPs for password recovery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // Sends an email containing a one-time password (OTP) for account recovery
    public void sendOtpEmail(String to, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Booknest Library: Access Recovery Code");
            message.setText("Greetings,\n\n" +
                    "You have requested a security reset for your Booknest account.\n" +
                    "Your verification code is: " + otp + "\n\n" +
                    "This code will expire in 10 minutes. If you did not request this, please secure your account immediately.\n\n" +
                    "Regards,\n" +
                    "Booknest Security Team");
            mailSender.send(message);
            log.info("OTP successfully dispatched to {}", to);
        } catch (Exception e) {
            log.error("Critical failure during OTP dispatch to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email dispatch failed: " + e.getMessage());
        }
    }

    // Sends a welcome email to a newly registered user using a rich HTML template
    public void sendWelcomeEmail(String to, String name) {
        try {
            Context context = new Context();
            context.setVariable("name", name);
            String htmlContent = templateEngine.process("welcome-email", context);

            sendHtmlEmail(to, "Welcome to the Booknest Library!", htmlContent);
            log.info("Welcome email successfully dispatched to {}", to);
        } catch (Exception e) {
            log.error("Failed to dispatch welcome email to {}: {}", to, e.getMessage());
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) throws jakarta.mail.MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(mimeMessage);
    }
}
