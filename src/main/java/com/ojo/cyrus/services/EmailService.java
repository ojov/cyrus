package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.ResendProperties;
import com.ojo.cyrus.exception.EmailSendingException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final Resend resend;
    private final TemplateEngine templateEngine;
    private final ResendProperties resendProperties;

    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);

        String htmlContent = templateEngine.process(templateName, context);
        CreateEmailOptions params = CreateEmailOptions.builder()
                        .from(resendProperties.fromEmail())
                        .to(to)
                        .subject(subject)
                        .html(htmlContent)
                        .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            log.info("Email sent successfully to {}. Message ID: {}", to, response.getId());
        } catch (ResendException e) {
            log.error("Failed to send email to {}", to, e);
            throw new EmailSendingException("Failed to send email", e);
        }
    }

    public void sendVerificationEmail(String to, String firstName, String verificationUrl) {
        Map<String, Object> variables = Map.of("firstName", firstName, "verificationUrl", verificationUrl);
        sendEmail(to, "Verify your Cyrus account", "email/verification", variables);
    }
}
