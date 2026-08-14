package com.erp.domain.notification.service.channel;

import com.erp.domain.notification.entity.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationChannelSenderRegistry {

    private final Map<NotificationChannel, NotificationChannelSender> senders;

    public NotificationChannelSenderRegistry(List<NotificationChannelSender> channelSenders) {
        EnumMap<NotificationChannel, NotificationChannelSender> senderMap = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelSender sender : channelSenders) {
            NotificationChannel channel = sender.channel();
            if (senderMap.containsKey(channel)) {
                throw new IllegalStateException("Duplicate notification sender configured for channel " + channel);
            }
            senderMap.put(channel, sender);
        }
        this.senders = Map.copyOf(senderMap);
    }

    public NotificationChannelSender getRequired(NotificationChannel channel) {
        NotificationChannelSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No notification sender configured for channel " + channel);
        }
        return sender;
    }
}
