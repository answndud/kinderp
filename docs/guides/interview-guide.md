# Backend Portfolio Interview Guide

기준일: 2026-08-14

이 문서는 면접관이 KinderP를 빠르게 평가할 때 볼 수 있는 백엔드 포트폴리오 설명 가이드입니다.

## 0. 이 프로젝트의 포지션

TownPet이 레거시 데이터와 서비스 구조를 안전하게 바꾸는 프로젝트라면, KinderP는 운영 중 여러 역할이 같은 tenant 데이터를 처리할 때 권한·상태·실패 복구를 통제하는 내부 플랫폼입니다. 면접에서는 기능 목록보다 학부모 요청 → 교사/원장 승인 → 감사 기록 → Outbox 전달/재시도의 닫힌 흐름을 먼저 보여줍니다. 전체 흐름과 현재 증거·남은 보완점은 [`docs/architecture/portfolio-story.md`](../architecture/portfolio-story.md)에 있습니다.

## 1. 5분 설명 루트

1. `README.md` 상단의 "30초 요약"으로 프로젝트 성격과 검증 상태를 먼저 설명합니다.
2. "핵심 문제와 해결" 표에서 권한 경계, 세션 revoke, 상태 전이, 감사 로그, outbox 운영을 짚습니다.
3. "수치로 검증한 개선" 표에서 Notepad/Dashboard query count와 CI 시간 개선을 설명합니다.
4. 화면 캡처에서 `/applications/pending`, `/notification-outbox`, audit 화면을 보여주며 실제 운영 흐름이 닫혀 있음을 설명합니다.
5. 코드 확인 요청이 들어오면 `docs/guides/evidence-map.md`에서 주장별 증거를 고른 뒤 `global/security`, `global/exception`, `domain/notification`, `domain/calendar`, `domain/attendance` 순서로 이동합니다.

## 2. 10분 시연 루트

1. `demo` 프로파일로 실행하고 원장 계정으로 로그인합니다.
2. `/attendance-requests`에서 학부모 요청과 교사/원장 승인 경계를 설명합니다.
3. `/applications/pending`에서 입학 신청의 상태 전이와 tenant 권한을 설명합니다.
4. `/notification-outbox`에서 timeline, status/channel/search 필터, dead-letter retry 버튼을 보여줍니다.
5. `/audit-logs`, `/domain-audit-logs`에서 요청부터 상태 전이·재시도까지 추적되는 방식을 보여줍니다.
6. 관련 통합 테스트에서 권한 실패와 상태 전이·동시성 결과를 확인합니다.
7. README의 성능 수치와 CI 결과를 보여주고, 실제 배포가 없는 부분은 준비된 자산과 미실행 범위를 구분해 설명합니다.

## 3. 말하기 스크립트

### 5분 버전

1. "이 프로젝트는 여러 역할이 같은 유치원 tenant를 처리할 때 권한과 업무 상태의 정합성을 보장하는 내부 운영 플랫폼입니다."
2. "학부모 요청은 tenant 경계 안에 저장되고, 교사나 원장의 승인만 허용된 상태 전이를 만들 수 있습니다."
3. "상태 전이는 감사 로그와 알림 Outbox로 이어지며, 외부 전달 실패는 dead-letter 운영 화면에서 원인과 재시도를 확인합니다."
4. "세션은 HTTP-only cookie JWT와 Redis refresh/session registry로 관리하고, 권한은 controller·service·integration test에서 함께 검증했습니다."
5. "TownPet이 레거시 이관과 구조 변경의 안전성을 보여준다면, 이 프로젝트는 운영 중 정합성·실패 복구·관측성을 보여줍니다."

### 10분 버전

1. "먼저 README의 30초 요약과 핵심 문제 표를 보겠습니다. 이 저장소의 핵심은 권한, 세션, 상태 전이, 감사, 관측성입니다."
2. "`/applications/pending`에서는 입학 신청이 승인, waitlist, offer로 전이되는 흐름을 보여줍니다. 이 상태 전이는 단순 update가 아니라 감사 로그와 알림까지 함께 닫힙니다."
3. "`/attendance-requests`에서는 학부모가 요청하고 교사/원장이 처리하는 경계를 보여줍니다. 권한 실패 케이스도 통합 테스트로 분리했습니다."
4. "`/notification-outbox`에서는 전체 timeline을 상태/채널/검색어로 좁혀 보고, dead-letter만 재시도하는 흐름을 보여줍니다."
5. "`/audit-logs`와 `/domain-audit-logs`에서는 인증 이벤트와 업무 이벤트를 분리하고, reason/summary 필터와 CSV export로 운영 조사를 이어갑니다."
6. "마지막으로 README의 성능 표와 CI 표를 보며, 개선 전후를 숫자로 남겼고 push CI는 빠르게, heavy 검증은 수동 workflow로 분리한 이유를 설명합니다."

## 4. 코드 리뷰 유도 포인트

| 관심사 | 먼저 볼 위치 | 설명할 포인트 |
| --- | --- | --- |
| 인증/세션 | `global/security`, `domain/auth` | cookie JWT, refresh rotation, Redis TTL, active session revoke |
| 권한 경계 | `AccessPolicyService`, `*IntegrationTest` | role + kindergarten tenant 검증을 service와 test 양쪽에 둠 |
| 예외 계약 | `global/exception` | 입력 오류를 500이 아니라 400 `ApiResponse.error`로 정규화 |
| Outbox 운영 | `domain/notification`, `/notification-outbox` | sender adapter, retry/backoff/dead-letter, timeline/search/filter, principal-only 운영 API |
| 성능 개선 | `src/test/java/com/kinderp/performance` | query count와 elapsed time 전후 측정 |
| CI 전략 | `.github/workflows/ci.yml`, `backend-quality.yml` | push quick check와 수동 heavy suite 분리 |

## 5. 약점 질문 선제 대응

면접관이 약점을 먼저 묻는다면 `docs/guides/risk-response.md`를 기준으로 답합니다.

| 약점 질문 | 짧은 답변 |
| --- | --- |
| 실제 배포가 없나요? | 비용 문제로 클라우드 배포는 하지 않았지만 Dockerfile, deploy compose, env contract, readiness, CD workflow_dispatch까지 준비했습니다. |
| Tailwind CDN 의존성은 운영에 부적절하지 않나요? | 초기 CDN 의존성을 제거하고 저장소 로컬 vendor asset과 Tailwind build 산출물로 전환했습니다. 외부 자산을 남길 경우에는 공식 digest/SRI를 별도로 검증합니다. |
| 모놀리식이 한계 아닌가요? | 현재 규모에서는 트랜잭션과 권한 경계를 한 저장소에서 닫는 것이 합리적이고, 분리한다면 notification/audit/reporting부터 분리합니다. |
| 외부 알림은 실제 발송인가요? | 실제 provider 연동보다 outbox 상태 전이, dead-letter 관측, 재시도 운영면을 검증하는 데 집중했습니다. |
| full test를 매번 안 돌려도 되나요? | push는 빠른 실패 신호, 큰 변경은 수동 quality workflow로 나눴습니다. 혼자 운영하는 main 프로젝트의 비용/피드백 균형입니다. |

## 6. 면접 질문 대응 포인트

| 질문 | 답변 방향 | 확인 위치 |
| --- | --- | --- |
| 왜 cookie JWT와 Redis를 같이 썼나요? | access token은 HTTP-only cookie로 노출면을 줄이고, refresh/session revoke는 Redis TTL과 active session registry로 제어합니다. | `global/security`, `domain/auth` |
| tenant 경계는 어디서 보장하나요? | Controller 권한, service requester 검증, repository 조회 조건, 통합 테스트를 같이 둡니다. | `AccessPolicyService`, `*ApiIntegrationTest` |
| 운영 중 알림 전송 실패는 어떻게 보나요? | outbox timeline에서 상태/채널/검색어로 좁혀 보고, dead-letter만 원장 전용 retry API/화면으로 재처리합니다. | `/notification-outbox`, `domain/notification` |
| 배포 전 CORS는 어떻게 바꾸나요? | `CORS_ALLOWED_ORIGINS`로 환경별 origin을 주입하고 credentialed CORS에서 wildcard를 쓰지 않습니다. | `SecurityConfig`, `env-contract.md` |
| 큰 service는 어떻게 관리했나요? | `KidApplicationService`는 신청/조회/취소/만료 orchestration을 맡고, review 상태 전이는 `KidApplicationReviewService`, 원생 등록/알림/audit은 보조 service로 분리했습니다. | `domain/kidapplication/service` |
| 성능 개선은 어떻게 증명했나요? | query count/elapsed time을 전후 측정하고 README와 performance smoke test에 남겼습니다. | README, `performance/*` |

## 7. 최근 강화 작업의 의도

### 입력/예외 하드닝

- 문제: 잘못된 query/path parameter, 필수 파라미터 누락, 날짜 변환 오류가 generic 500으로 떨어질 수 있었습니다.
- 결정: Spring MVC 입력 경계 예외를 `GlobalExceptionHandler`에서 명시적으로 400 `ApiResponse.error`로 닫았습니다.
- 검증: 출석 월 조회 invalid month, missing year, invalid year type 통합 테스트를 추가했습니다.
- 트레이드오프: 모든 `IllegalArgumentException`을 400으로 잡지 않고, MVC 입력 오류와 날짜 오류 중심으로 제한했습니다.

### 캘린더 반복/범위 안전장치

- 문제: 반복 일정 확장 로직이 `CalendarEventService`에 섞여 있었고, 긴 조회 범위를 제한하지 않았습니다.
- 결정: `RecurrenceExpander`로 반복 occurrence 확장을 분리하고, 일정 목록 조회 기간을 최대 366일로 제한했습니다.
- 검증: fast unit test로 반복 확장을 고정하고, 통합 테스트로 366일 초과 조회가 400인지 확인했습니다.
- 트레이드오프: 신규 반복 규칙을 추가하지 않고 기존 `DAILY/WEEKLY/MONTHLY` 동작 보존에 집중했습니다.

### Notification Outbox 운영 API

- 문제: outbox는 retry/backoff/dead-letter 상태 전이를 갖췄지만, 운영자가 실패 건을 확인하고 재시도하는 API가 없었습니다.
- 결정: 원장 전용 `/api/v1/notification-outbox` 운영 API와 `/notification-outbox` 화면을 추가해 summary, timeline, 상태/채널/검색 필터, dead-letter 수동 retry를 제공합니다.
- 검증: 원장 성공, 교사 접근 차단, timeline status/channel/q 필터, dead-letter 재시도 상태 전이, view endpoint 접근을 통합 테스트로 확인했습니다.
- 트레이드오프: 실제 외부 provider 연동은 adapter 경계 뒤에 두고, 이번 구현은 관측/검색/재시도 운영면에 집중했습니다.

### CORS 운영 설정

- 문제: allowed origin이 `http://localhost:8080`으로 하드코딩되어 실제 배포 도메인 전환 시 코드 변경이 필요했습니다.
- 결정: `app.security.cors.allowed-origins`와 `CORS_ALLOWED_ORIGINS`로 분리했습니다.
- 검증: property override가 `CorsConfigurationSource`에 반영되는지 context 테스트로 확인했습니다.
- 트레이드오프: credentialed CORS를 유지하므로 wildcard origin은 허용하지 않습니다.

### KidApplication workflow 분리

- 문제: 입학 신청 service에 상태 전이, 원생 생성, 학부모 활성화, 알림, audit 기록이 함께 섞여 있었습니다.
- 결정: admission, notification, audit 보조 service와 review 상태 전이 service를 추출하고 `KidApplicationService`는 신청/조회/취소/만료 orchestration 중심으로 남겼습니다.
- 검증: 기존 `KidApplicationApiIntegrationTest`로 approve/waitlist/offer/accept/권한 동작을 보존했습니다.
- 트레이드오프: public API와 DB schema는 건드리지 않는 보존형 리팩토링으로 제한했습니다.

### 코드 탐색 노이즈 제거

- 문제: 비어 있는 `MemberRepositoryCustom/Impl` 스텁과 로컬 swap/backup 파일이 코드 탐색을 방해했습니다.
- 결정: 사용되지 않는 custom repository 스텁을 제거하고 로컬 ignored 작업파일을 삭제했습니다.
- 검증: 컴파일과 targeted tests로 repository wiring 회귀를 확인합니다.

## 8. 면접에서 강조할 답변 포인트

- 보안: HTTP-only cookie JWT, Redis refresh session, active session revoke, fail-closed profile.
- 권한: `PRINCIPAL`, `TEACHER`, `PARENT` 역할과 유치원 tenant 경계를 API/service/test에서 함께 검증.
- 운영성: audit log, outbox retry/dead-letter, Prometheus/Grafana, readiness, structured logging.
- 성능: Notepad/Dashboard N+1 제거, query count와 응답 시간 전후 비교, push CI 경량화.
- 테스트: Testcontainers 기반 MySQL/Redis 통합 테스트, fast/integration/performance smoke 분리.
- 문서화: 루트 `PLAN.md`로 현재/향후 구현 작업을 관리하고 완료 항목은 제거.

## 9. 남은 리스크와 후속 개선

- 실제 클라우드 배포는 비용 문제로 아직 실행하지 않았고, 배포 자산과 runbook 중심으로 준비된 상태입니다.
- Notification Outbox 운영 화면은 timeline, 상태/채널/검색 필터, dead-letter retry까지 제공합니다. 실제 provider adapter 운영 검증은 후속 개선입니다.
- `KidApplicationService`는 핵심 review 상태 전이를 분리했지만, 더 큰 규모에서는 상태 machine library나 workflow engine 도입을 검토할 수 있습니다.
- 실제 운영 도메인이 생기면 `CORS_ALLOWED_ORIGINS`, OAuth redirect URI, secure cookie, reverse proxy 설정을 함께 검증해야 합니다.
- 세부 약점 대응은 `docs/guides/risk-response.md`, 주장별 증거 연결은 `docs/guides/evidence-map.md`를 기준으로 확인합니다.
