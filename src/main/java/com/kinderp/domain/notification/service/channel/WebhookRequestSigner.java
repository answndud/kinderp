package com.kinderp.domain.notification.service.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookRequestSigner {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public HttpHeaders createHeaders(Object body, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Webhook signature secret is not configured");
        }

        try {
            String timestamp = Long.toString(Instant.now(clock).getEpochSecond());
            byte[] payload = objectMapper.writeValueAsBytes(body);
            String signature = sign(timestamp + "." + new String(payload, StandardCharsets.UTF_8), secret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(TIMESTAMP_HEADER, timestamp);
            headers.set(SIGNATURE_HEADER, "v1=" + signature);
            return headers;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Webhook payload could not be serialized", exception);
        }
    }

    private String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Webhook signature could not be created", exception);
        }
    }
}
