package com.booknest.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Service for sending transactional emails asynchronously.
 * Uses Thymeleaf templates and JavaMailSender to dispatch high-fidelity HTML emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @org.springframework.beans.factory.annotation.Value("${FRONTEND_URL:http://localhost:4200}")
    private String frontendUrl;

    /**
     * Sends an order confirmation email asynchronously.
     *
     * @param to        Recipient email address
     * @param subject   Email subject
     * @param variables Variables to be injected into the Thymeleaf template
     */
    @Async
    public void sendOrderConfirmation(String to, String subject, Map<String, Object> variables) {
        log.info("Starting asynchronous mail dispatch to: {}", to);
        try {
            Context context = new Context();
            context.setVariables(variables);
            context.setVariable("frontendUrl", frontendUrl);
            String htmlContent = templateEngine.process("transactional-order-confirmation", context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            log.info("Transactional order confirmation successfully sent to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to prepare or send transactional email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during mail dispatch to {}: {}", to, e.getMessage());
        }
    }
}
