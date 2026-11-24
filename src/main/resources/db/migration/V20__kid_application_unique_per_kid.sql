-- ============================================
-- V20: 입학 신청 중복 기준을 학부모에서 원생으로 변경
-- ============================================

-- 기존 규칙(V3)은 (학부모, 유치원)당 1건이라 형제를 같은 유치원에 신청할 수 없고,
-- soft delete한 행도 키를 계속 점유했다. 새 규칙: 같은 학부모·유치원·원생의 "삭제되지 않은" 신청은 1건.

-- 방어적 정리: 같은 학부모·유치원·원생의 미삭제 중복이 있으면 최신 1건만 남긴다.
UPDATE kid_application ka
JOIN (
    SELECT parent_id, kindergarten_id, kid_name, birth_date, MAX(id) AS keep_id
    FROM kid_application
    WHERE deleted_at IS NULL
    GROUP BY parent_id, kindergarten_id, kid_name, birth_date
    HAVING COUNT(*) > 1
) dup
ON ka.parent_id = dup.parent_id
AND ka.kindergarten_id = dup.kindergarten_id
AND ka.kid_name = dup.kid_name
AND ka.birth_date = dup.birth_date
AND ka.id <> dup.keep_id
AND ka.deleted_at IS NULL
SET ka.deleted_at = NOW();

-- 삭제되지 않은 행만 1, 삭제된 행은 NULL. MySQL UNIQUE는 NULL끼리 충돌하지 않으므로
-- 삭제된 신청은 키에서 빠진다 (V14의 pending_request_date와 같은 기법).
ALTER TABLE kid_application
    ADD COLUMN active_marker TINYINT
        GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED;

-- parent_id FK가 쓰는 인덱스를 잃지 않도록 새 키(parent_id가 선두 컬럼)를 같은 문장에서 추가한다.
ALTER TABLE kid_application
    ADD UNIQUE KEY uk_kid_application_active_kid (parent_id, kindergarten_id, kid_name, birth_date, active_marker),
    DROP INDEX uk_parent_kindergarten;
