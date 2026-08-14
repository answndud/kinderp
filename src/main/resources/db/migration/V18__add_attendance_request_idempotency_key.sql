ALTER TABLE attendance_change_request
    ADD COLUMN idempotency_key VARCHAR(100) NULL;

CREATE UNIQUE INDEX uk_attendance_change_request_requester_idempotency
    ON attendance_change_request (requester_id, idempotency_key);
