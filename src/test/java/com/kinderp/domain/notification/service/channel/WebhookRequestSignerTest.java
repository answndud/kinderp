package com.kinderp.domain.notification.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WebhookRequestSignerTest {

    private final WebhookRequestSigner signer = new WebhookRequestSigner(new ObjectMapper());

    @Test
    void createsTimestampedHmacHeaders() {
        var headers = signer.createHeaders(new Payload("notification"), "test-secret");

        assertThat(headers.getFirst("Content-Type")).startsWith("application/json");
        assertThat(headers.getFirst("X-Webhook-Timestamp")).matches("\\d+");
        assertThat(headers.getFirst("X-Webhook-Signature")).matches("v1=[0-9a-f]{64}");
    }

    @Test
    void rejectsMissingSecret() {
        assertThatIllegalStateException()
                .isThrownBy(() -> signer.createHeaders(new Payload("notification"), " "))
                .withMessage("Webhook signature secret is not configured");
    }

    private record Payload(String value) {
    }
}
