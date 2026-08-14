# 핵심 업무 workflow 상태 전이

상태값을 화면에서 임의로 변경하지 않고 entity의 전이 메서드가 허용된 시작 상태를 검사한다. 서비스는 row lock과 tenant·role 검증을 먼저 수행하고, 전이 성공 후 알림과 domain audit을 기록한다.

## 출결 변경 요청

| 현재 상태 | 승인 | 거절 | 취소 |
| --- | --- | --- | --- |
| `PENDING` | `APPROVED` | `REJECTED` | `CANCELLED` |
| `APPROVED` | 차단 | 차단 | 차단 |
| `REJECTED` | 차단 | 차단 | 차단 |
| `CANCELLED` | 차단 | 차단 | 차단 |

구현 위치:

- 상태 전이: `AttendanceChangeRequest.approve/reject/cancel`
- 동시 처리: `AttendanceChangeRequestRepository.findByIdForUpdate`
- tenant·role 검증: `AccessPolicyService.validateAttendanceChangeRequestReviewAccess`
- 중복 pending 방지: `V14__guard_pending_attendance_change_requests.sql`
- 회귀 테스트: `WorkflowStateTransitionTest`, `AttendanceChangeRequestApiIntegrationTest`

## 입학 신청

| 현재 상태 | 즉시 승인 | 대기열 | offer | offer 수락 | 거절 | 취소 |
| --- | --- | --- | --- | --- | --- | --- |
| `PENDING` | 가능 | 가능 | 가능 | 불가 | 가능 | 가능 |
| `WAITLISTED` | 불가 | 불가 | 가능 | 불가 | 가능 | 가능 |
| `OFFERED` | 불가 | 불가 | 불가 | 가능 | 가능 | 가능 |
| `OFFER_EXPIRED` | 불가 | 가능 | 불가 | 불가 | 불가 | 불가 |
| `APPROVED` | 불가 | 불가 | 불가 | 불가 | 불가 | 불가 |
| `REJECTED` | 불가 | 불가 | 불가 | 불가 | 불가 | 불가 |
| `CANCELLED` | 불가 | 불가 | 불가 | 불가 | 불가 | 불가 |

구현 위치:

- 상태 전이: `KidApplication.approveDirect/placeOnWaitlist/offerSeat/acceptOffer/reject/cancel`
- 동시 처리: `KidApplicationRepository.findByIdAndDeletedAtIsNullForUpdate`
- 정원 경합 제어: `ClassroomCapacityService.lockClassroom`
- tenant·role 검증: `KidApplicationReviewService.getStaffReviewer`
- 회귀 테스트: `WorkflowStateTransitionTest`, `KidApplicationApiIntegrationTest`

## Outbox 운영 scope

원장 전용이라는 URL 권한만으로 충분하지 않다. summary, timeline, dead-letter, retry는 모두 로그인한 원장의 `kindergartenId`를 repository 조건으로 전달한다. 다른 tenant의 ID를 직접 retry해도 scoped lookup에서 찾지 못해 동일한 not-found 계약으로 종료된다.

재시도는 `PESSIMISTIC_WRITE`로 대상 row를 선점하고, `NOTIFICATION_OUTBOX_RETRIED` 업무 감사 이벤트를 남긴다. 따라서 중복 클릭이나 운영자 교대가 발생해도 상태 변경 주체와 시도 횟수를 조사할 수 있다.
