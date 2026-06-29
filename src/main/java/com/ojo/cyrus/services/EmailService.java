package com.ojo.cyrus.services;

import java.util.Map;

public interface EmailService {
    void sendEmail(String to, String subject, String templateName, Map<String, Object> variables);
    void sendVerificationEmail(String to, String firstName, String verificationUrl);
}
