# API 접근·tenant 검증 matrix

기준일: 2026-08-14

이 문서는 Controller의 URL 권한만 나열하지 않는다. 각 API가 어떤 요청자 경계를 가져야 하는지와, 그 경계가 service/test에서 실제로 확인되는 위치를 함께 기록한다. 새로운 endpoint를 추가할 때는 아래 세 칸을 모두 채운 뒤 구현한다.

## 접근 규칙

| 규칙 | 의미 |
| --- | --- |
| URL role | `@PreAuthorize`가 인증 여부와 기본 역할을 제한한다. |
| requester scope | service가 JWT 요청자의 member ID를 기준으로 본인·자녀·교실·유치원 소속을 재검증한다. |
| state/ownership | 상태 전이와 생성자/수신자/담임/원장 소유권을 별도로 검증한다. |
| repository scope | 목록/검색은 가능한 경우 tenant 또는 본인 조건을 query에 포함한다. |
| cross-tenant proof | 다른 유치원 요청이 403 또는 도메인 접근 오류로 거절되는 통합 테스트가 있어야 한다. |

## 도메인 API matrix

| API family | URL / method | 허용 역할 | service 경계 | 대표 증거 |
| --- | --- | --- | --- | --- |
| 인증 | `/api/v1/auth/signup`, `/login`, `/refresh` | 공개 | rate limit, 입력 검증, refresh/session 정책 | `AuthApiIntegrationTest` |
| 인증 세션 | `/api/v1/auth/me`, `/sessions/**` | 인증 사용자 | 요청자 member ID만 사용, session ID 소유권 확인 | `AuthApiIntegrationTest` |
| 회원 자기 관리 | `/api/v1/members/me`, `/profile`, `/password/**`, `/withdraw` | 인증 사용자 | 요청자 본인만 변경 | `MemberApiIntegrationTest` |
| 부모 목록 | `/api/v1/members/parents` | PRINCIPAL, TEACHER | 요청자 유치원과 동일한 부모만 조회 | `MemberApiController`, `MemberService` |
| 유치원 관리 | `/api/v1/kindergartens/**` | 조회: 인증, 변경: PRINCIPAL | 요청자 tenant; 원장 변경 소유권 | `KindergartenApiIntegrationTest`의 tenant 경계 테스트 |
| 반 관리 | `/api/v1/classrooms/**` | staff / 인증 조회 | 교실의 kindergarten과 요청자 소속 비교; 담임 배정은 원장 | `ClassroomApiIntegrationTest` |
| 원생 | `/api/v1/kids/**` | staff / 부모 조회 | staff tenant, 부모-자녀 관계, classroom 소속 검증 | `KidApiIntegrationTest` |
| 출결 | `/api/v1/attendance/**` | staff 변경, 인증 조회 | 원생·교실 tenant와 요청자 비교; 부모는 본인 자녀 범위 | `AttendanceApiIntegrationTest` |
| 출결 변경 요청 | `/api/v1/attendance-change-requests/**` | 부모 생성/취소, staff 처리 | requester/child ownership, staff tenant, 상태 전이, idempotency key | `AttendanceChangeRequestApiIntegrationTest` |
| 알림장 | `/api/v1/notepads/**` | staff 작성, 인증 조회, 부모 읽음 | writer/child/classroom/tenant 접근 정책 | `NotepadApiIntegrationTest` |
| 공지 | `/api/v1/announcements/**` | staff 작성·변경, 인증 조회 | writer와 대상 kindergarten 동일성, 조회 tenant | `AnnouncementApiIntegrationTest` |
| 알림 | `/api/v1/notifications/**` | 생성: staff, 나머지 인증 | 수신자 본인 조회·변경; 생성자는 같은 tenant staff | `NotificationApiIntegrationTest` |
| 캘린더 | `/api/v1/calendar/events/**` | scope별 staff/부모 | kindergarten/classroom/personal scope, creator·담임·자녀 관계 | `CalendarApiIntegrationTest` |
| 교사 지원 | `/api/v1/kindergarten-applications/**` | TEACHER / PRINCIPAL | 지원자 본인, 원장 tenant, 상태 전이 | `KindergartenApplicationApiIntegrationTest` |
| 원생 입학 | `/api/v1/kid-applications/**` | PARENT / staff | 신청자 본인 또는 같은 tenant staff, 정원·상태 전이 | `KidApplicationApiIntegrationTest` |
| 인증 감사 | `/api/v1/auth/audit-logs/**` | PRINCIPAL | 요청자 tenant의 audit만 조회/export | `AuthAuditApiIntegrationTest` |
| 업무 감사 | `/api/v1/domain-audit-logs/**` | PRINCIPAL | 요청자 tenant의 domain audit만 조회/export | `DomainAuditApiIntegrationTest` |
| 대시보드 | `/api/v1/dashboard/statistics` | PRINCIPAL | 요청자 kindergarten 집계만 반환 | `DashboardApiIntegrationTest` |
| Outbox 운영 | `/api/v1/notification-outbox/**` | PRINCIPAL | 요청자 kindergarten의 timeline/dead-letter만 조회·재시도 | `NotificationOutboxOpsApiIntegrationTest` |

## 교차 tenant 검증 coverage

현재 통합 테스트에서 타 유치원 접근을 직접 확인하는 주요 영역은 다음과 같다.

| 영역 | 조회 | 변경 | 근거 |
| --- | --- | --- | --- |
| 유치원 | O | O | `KindergartenApiIntegrationTest` |
| 원생/출결 | O | O | `KidApiIntegrationTest`, `AttendanceApiIntegrationTest` |
| 공지/알림장 | O | O | `AnnouncementApiIntegrationTest`, `NotepadApiIntegrationTest` |
| 알림/Outbox | 본인 수신자·tenant 생성 | O | `NotificationApiIntegrationTest`, `NotificationOutboxOpsApiIntegrationTest` |
| 캘린더 | O | O | `CalendarApiIntegrationTest` |
| 입학 workflow | O | O | `KidApplicationApiIntegrationTest`, `KindergartenApplicationApiIntegrationTest` |
| 감사/대시보드 | O | export/집계 | `AuthAuditApiIntegrationTest`, `DomainAuditApiIntegrationTest`, `DashboardApiIntegrationTest` |

## 신규 API 체크리스트

- [ ] Controller에 `@PreAuthorize`를 선언했는가?
- [ ] `CustomUserDetails`의 member ID를 service까지 전달하는가?
- [ ] service에서 role만이 아니라 tenant/본인/자녀/교실 소유권을 검증하는가?
- [ ] 목록 query가 요청자 tenant를 벗어나지 않는가?
- [ ] 성공 케이스와 타 tenant 또는 타 사용자 실패 케이스를 함께 테스트했는가?
- [ ] 상태 전이 API라면 중복 요청·잘못된 순서·동시 요청을 검증했는가?
- [ ] README/evidence map에 실제 코드·테스트 경로를 연결했는가?

## 한계

- 이 문서는 코드와 현재 테스트의 정적 대조표이며, 외부 HTTPS 환경의 authorization proxy나 실제 IAM 정책을 대신하지 않는다.
- 전체 endpoint의 모든 조합을 증명한다고 주장하지 않는다. 새로운 역할·상태·scope가 추가되면 해당 행과 테스트를 함께 갱신해야 한다.
