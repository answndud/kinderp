package com.erp.domain.notification.service.channel;

import com.erp.domain.notification.entity.NotificationChannel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NotificationChannelSenderRegistry 테스트")
@Tag("fast")
class NotificationChannelSenderRegistryTest {

    @Test
    @DisplayName("채널에 맞는 sender를 반환한다")
    void getRequired_ReturnsSenderForChannel() {
        StubSender appSender = new StubSender(NotificationChannel.APP);
        NotificationChannelSenderRegistry registry = new NotificationChannelSenderRegistry(List.of(appSender));

        assertThat(registry.getRequired(NotificationChannel.APP)).isSameAs(appSender);
    }

    @Test
    @DisplayName("채널에 맞는 sender가 없으면 실패한다")
    void getRequired_Fails_WhenSenderMissing() {
        NotificationChannelSenderRegistry registry = new NotificationChannelSenderRegistry(List.of(
                new StubSender(NotificationChannel.APP)
        ));

        assertThatThrownBy(() -> registry.getRequired(NotificationChannel.EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No notification sender configured for channel EMAIL");
    }

    @Test
    @DisplayName("같은 채널 sender가 중복 등록되면 실패한다")
    void constructor_Fails_WhenDuplicateChannelExists() {
        assertThatThrownBy(() -> new NotificationChannelSenderRegistry(List.of(
                new StubSender(NotificationChannel.APP),
                new StubSender(NotificationChannel.APP)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate notification sender configured for channel APP");
    }

    private record StubSender(NotificationChannel channel) implements NotificationChannelSender {

        @Override
        public void send(NotificationDeliveryPayload payload) {
            // No-op test adapter.
        }
    }
}
