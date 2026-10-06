package com.example.lori.service;

import com.example.lori.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Gui email giao dich qua Brevo HTTPS API (POST /v3/smtp/email, xac thuc bang header api-key).
 * Khong dung SMTP vi Render Free chan cac cong SMTP.
 */
@Slf4j
@Service
public class EmailService {

    private static final String BREVO_SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String senderEmail;
    private final String senderName;

    public EmailService(
            @Value("${app.mail.brevo-api-key}") String apiKey,
            @Value("${app.mail.sender-email}") String senderEmail,
            @Value("${app.mail.sender-name}") String senderName) {
        // Dat timeout de request khong treo mai neu Brevo cham/khong phan hoi
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("api-key", apiKey)
                .build();
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    /** Gui ma xac nhan dat lai mat khau (code chi gom chu so). Loi gui -> 503 de app cho nguoi dung thu lai. */
    public void sendPasswordResetCode(String toEmail, String code, long validMinutes) {
        String htmlContent = "<div style=\"font-family:Arial,sans-serif;font-size:15px;color:#222\">"
                + "<p>Xin chào,</p>"
                + "<p>Mã xác nhận đặt lại mật khẩu Lori của bạn là:</p>"
                + "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px\">" + code + "</p>"
                + "<p>Mã có hiệu lực trong " + validMinutes + " phút. "
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>"
                + "</div>";

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "Mã đặt lại mật khẩu Lori",
                "htmlContent", htmlContent);

        try {
            restClient.post()
                    .uri(BREVO_SEND_EMAIL_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Chi log thong bao loi, khong log API key
            log.error("Gui email qua Brevo that bai: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send email, try again later");
        }
    }
}