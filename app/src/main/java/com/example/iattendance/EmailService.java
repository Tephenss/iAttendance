package com.example.iattendance;

import android.os.AsyncTask;
import android.util.Log;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailService {
    private static final String TAG = "EmailService";
    
    // Gmail SMTP configuration
    private static final String HOST = "smtp.gmail.com";
    private static final String PORT = "587";
    private static final String USERNAME = "iattendancemanagement@gmail.com";
    private static final String PASSWORD = "vlxhslqwkzrgwuhj";
    
    public interface EmailCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
    
    public static void sendVerificationEmail(String recipientEmail, String verificationCode, String fullName, EmailCallback callback) {
        new SendEmailTask(recipientEmail, verificationCode, fullName, callback).execute();
    }
    
    private static class SendEmailTask extends AsyncTask<Void, Void, Boolean> {
        private String recipientEmail;
        private String verificationCode;
        private String fullName;
        private EmailCallback callback;
        private String errorMessage;
        
        public SendEmailTask(String recipientEmail, String verificationCode, String fullName, EmailCallback callback) {
            this.recipientEmail = recipientEmail;
            this.verificationCode = verificationCode;
            this.fullName = fullName;
            this.callback = callback;
        }
        
        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                // Set up email properties
                Properties props = new Properties();
                props.put("mail.smtp.host", HOST);
                props.put("mail.smtp.port", PORT);
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.trust", HOST);
                
                // Create session with authentication
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(USERNAME, PASSWORD);
                    }
                });
                
                // Create message
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(USERNAME));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject("iAttendance - Email Verification Code");
                
                // Create HTML email content
                String htmlContent = createEmailContent(fullName, verificationCode);
                message.setContent(htmlContent, "text/html; charset=utf-8");
                
                // Send email
                Transport.send(message);
                
                Log.d(TAG, "Email sent successfully to: " + recipientEmail);
                return true;
                
            } catch (MessagingException e) {
                Log.e(TAG, "Failed to send email: " + e.getMessage());
                errorMessage = e.getMessage();
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error: " + e.getMessage());
                errorMessage = e.getMessage();
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                callback.onSuccess();
            } else {
                callback.onError(errorMessage != null ? errorMessage : "Failed to send email");
            }
        }
    }
    
    private static String createEmailContent(String fullName, String verificationCode) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }" +
                ".email-container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 10px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); overflow: hidden; }" +
                ".header { background: linear-gradient(135deg, #00b341 0%, #009933 100%); padding: 40px 30px; text-align: center; }" +
                ".logo { color: white; font-size: 32px; font-weight: bold; margin-bottom: 10px; text-shadow: 0 2px 4px rgba(0,0,0,0.3); }" +
                ".subtitle { color: rgba(255,255,255,0.9); font-size: 16px; margin-bottom: 20px; }" +
                ".content { padding: 40px 30px; }" +
                ".welcome-text { color: #333; font-size: 24px; font-weight: bold; margin-bottom: 20px; text-align: center; }" +
                ".instruction-text { color: #666; font-size: 16px; line-height: 1.6; margin-bottom: 30px; text-align: center; }" +
                ".verification-code-container { text-align: center; margin: 30px 0; }" +
                ".verification-code-label { color: #00b341; font-size: 14px; font-weight: bold; margin-bottom: 10px; text-transform: uppercase; letter-spacing: 1px; }" +
                ".verification-code { background: linear-gradient(135deg, #00b341 0%, #009933 100%); color: white; font-size: 28px; font-weight: bold; text-align: center; padding: 25px; border-radius: 10px; margin: 15px auto; letter-spacing: 4px; display: inline-block; min-width: 200px; box-shadow: 0 4px 15px rgba(0,179,65,0.3); }" +
                ".timer-warning { background-color: #fff3cd; border-left: 4px solid #ffc107; color: #856404; padding: 15px; border-radius: 5px; margin: 20px 0; font-size: 14px; }" +
                ".security-notice { background-color: #f8f9fa; border: 1px solid #dee2e6; color: #495057; padding: 20px; border-radius: 8px; margin: 25px 0; font-size: 14px; }" +
                ".footer { background-color: #f8f9fa; padding: 30px; text-align: center; border-top: 1px solid #dee2e6; }" +
                ".footer-text { color: #6c757d; font-size: 12px; line-height: 1.5; margin-bottom: 10px; }" +
                ".footer-brand { color: #00b341; font-weight: bold; }" +
                ".divider { height: 1px; background: linear-gradient(to right, transparent, #00b341, transparent); margin: 20px 0; }" +
                "@media (max-width: 600px) { .email-container { margin: 10px; } .content { padding: 20px; } .verification-code { font-size: 24px; padding: 20px; } }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='email-container'>" +
                "<div class='header'>" +
                "<div class='logo'>iAttendance</div>" +
                "<div class='subtitle'>Laguna University Attendance Management System</div>" +
                "</div>" +
                "<div class='content'>" +
                "<div class='welcome-text'>Welcome to iAttendance!</div>" +
                "<div class='instruction-text'>Hello <strong>" + fullName + "</strong>,<br>To complete your registration and secure your account, please verify your email address using the verification code below:</div>" +
                "<div class='verification-code-container'>" +
                "<div class='verification-code-label'>Verification Code</div>" +
                "<div class='verification-code'>" + verificationCode + "</div>" +
                "</div>" +
                "<div class='divider'></div>" +
                "<div class='timer-warning'>" +
                "<strong>⏰ Time Sensitive:</strong> This verification code will expire in <strong>30 minutes</strong> for security reasons." +
                "</div>" +
                "<div class='security-notice'>" +
                "<strong>🔒 Security Notice:</strong><br>" +
                "• Never share this verification code with anyone<br>" +
                "• iAttendance will never ask for your verification code via phone or email<br>" +
                "• If you didn't request this code, please ignore this email or contact our support team" +
                "</div>" +
                "<div style='text-align: center; margin-top: 30px; color: #666; font-size: 14px;'>" +
                "Thank you for using iAttendance!<br>" +
                "We're excited to have you on board." +
                "</div>" +
                "</div>" +
                "<div class='footer'>" +
                "<div class='footer-text'>" +
                "This email was sent from <span class='footer-brand'>iAttendance Management System</span><br>" +
                "Laguna University Attendance Management System" +
                "</div>" +
                "<div class='footer-text'>" +
                "© 2024 iAttendance. All rights reserved." +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
