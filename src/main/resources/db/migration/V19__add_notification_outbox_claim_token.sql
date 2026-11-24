-- ============================================
-- V19: notification_outbox claim token (발송 결과 기록 fencing)
-- ============================================

-- worker가 outbox를 claim할 때마다 새 토큰을 발급한다.
-- 발송 결과는 "아직 내 토큰이 유효한 PROCESSING 행"에만 반영해, timeout 후 다른 worker가 재claim한 건을
-- 이전 worker가 덮어쓰지 못하게 한다.
ALTER TABLE notification_outbox
    ADD COLUMN claim_token VARCHAR(36) NULL;
