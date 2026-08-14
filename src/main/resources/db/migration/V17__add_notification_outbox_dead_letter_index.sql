CREATE INDEX idx_notification_outbox_dead_letter_timeline
    ON notification_outbox(status, channel, dead_lettered_at, id);
