package com.vyapaaros.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String recipientEmail, String otp, Duration validity) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                false,
                StandardCharsets.UTF_8.name()
            );
            helper.setTo(recipientEmail);
            helper.setSubject("Your VyapaarOS verification code");
            helper.setText(buildOtpEmail(otp, validity), true);
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            throw new IllegalStateException(
                "Unable to deliver the verification email.",
                exception
            );
        }
    }

    private String buildOtpEmail(String otp, Duration validity) {
        long validityMinutes = Math.max(1, validity.toMinutes());
        return """
            <!doctype html>
            <html lang="en">
              <body style="margin:0;background:#f5f5f0;color:#202923;font-family:Arial,sans-serif;">
                <div style="max-width:560px;margin:32px auto;padding:32px;background:#ffffff;border:1px solid #dce5df;border-radius:12px;">
                  <p style="margin:0 0 24px;color:#173f35;font-size:20px;font-weight:700;">VyapaarOS</p>
                  <h1 style="margin:0 0 12px;color:#173f35;font-size:24px;">Verify your email</h1>
                  <p style="margin:0 0 24px;color:#737b75;font-size:15px;line-height:1.6;">
                    Use the verification code below to continue securely.
                  </p>
                  <p style="margin:0 0 24px;padding:16px;text-align:center;background:#eaf2ed;border-radius:8px;color:#173f35;font-size:32px;font-weight:700;letter-spacing:8px;">
                    %s
                  </p>
                  <p style="margin:0;color:#737b75;font-size:13px;line-height:1.6;">
                    This code expires in %d minutes. If you did not request this email, you can safely ignore it.
                  </p>
                </div>
              </body>
            </html>
            """.formatted(otp, validityMinutes);
    }
}
