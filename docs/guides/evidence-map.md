# Evidence Map

기준일: 2026-08-14

이 문서는 README와 면접 답변에서 하는 강한 주장을 코드, 테스트, 문서, 화면 경로와 연결하기 위한 증거 지도입니다.

## 대표 서사

KinderP의 대표 질문은 “하나의 유치원 tenant를 여러 역할이 동시에 처리할 때 권한과 업무 상태의 정합성을 어떻게 보장하는가”입니다. TownPet의 대표 서사인 레거시 이관·모듈 경계·복구와 겹치지 않도록, 이 저장소에서는 승인 workflow, 동시성, 감사, Outbox 운영을 하나의 흐름으로 설명합니다. 상세 흐름은 [`docs/architecture/portfolio-story.md`](../architecture/portfolio-story.md)를 기준으로 합니다.

## 1. 핵심 주장별 증거

| 주장 | 코드 증거 | 테스트 증거 | 문서/시연 증거 | 면접에서 보여줄 순서 |
| --- | --- | --- | --- | --- |
| 역할/tenant 경계를 API와 service에서 함께 검증했다. | `src/main/java/com/kinderp/global/security/access/AccessPolicyService.java`, `src/main/java/com/kinderp/domain/*/service/*Service.java` | `src/test/java/com/kinderp/api/*IntegrationTest.java` | `README.md` 핵심 문제와 해결, `docs/guides/demo-scenario.md`, `docs/architecture/api-access-matrix.md` | 권한 질문이 나오면 matrix에서 Controller → service → test 순서로 이동 |
| 입학 신청 review 상태 전이는 별도 workflow service로 분리했다. | `src/main/java/com/kinderp/domain/kidapplication/service/KidApplicationService.java`, `src/main/java/com/kinderp/domain/kidapplication/service/KidApplicationReviewService.java`, `KidApplicationAdmissionService`, `KidApplicationNotificationService`, `KidApplicationAuditService` | `src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java` | `docs/guides/interview-guide.md` | 큰 service 질문이 나오면 service 분리 전후 책임을 설명 |
| 세션은 cookie JWT와 Redis refresh/session registry로 revoke 가능하게 설계했다. | `src/main/java/com/kinderp/global/security/jwt/*`, `src/main/java/com/kinderp/domain/auth/*` | `src/test/java/com/kinderp/api/AuthApiIntegrationTest.java` | `README.md` 아키텍처 요약, `docs/guides/interview-guide.md` | 로그인/refresh/session 종료 API 흐름 설명 |
| 공개 signup과 login/refresh 인증 남용을 제한하고 재시도 시점을 API 계약으로 제공한다. | `src/main/java/com/kinderp/domain/auth/service/AuthRateLimitService.java`, `AuthRateLimitProperties`, `src/main/java/com/kinderp/global/exception/GlobalExceptionHandler.java` | `AuthApiIntegrationTest`의 signup/login/refresh rate-limit 및 `Retry-After`, `AuthRateLimitPropertiesTest`의 양수 설정 검증 | `README.md`, `docs/guides/env-contract.md`, `ErrorCode.AUTH_RATE_LIMITED` | 429 응답과 Redis window를 함께 설명 |
| 입력 오류를 500이 아니라 400 계약으로 닫았다. | `src/main/java/com/kinderp/global/exception/GlobalExceptionHandler.java`, `src/main/java/com/kinderp/global/exception/ErrorCode.java` | 출석 월 조회 invalid/missing/type 통합 테스트 | `README.md`, 관련 통합 테스트 | 예외 처리 질문에서 global handler와 테스트를 함께 제시 |
| 캘린더 긴 조회 범위를 제한했다. | `src/main/java/com/kinderp/domain/calendar/*`, `RecurrenceExpander` | calendar fast/integration tests | `README.md`, 관련 통합 테스트 | 성능/안전장치 질문에서 366일 cap 설명 |
| Notification Outbox timeline과 dead-letter를 운영자가 검색/필터/재시도할 수 있다. | `src/main/java/com/kinderp/domain/notification/controller/NotificationOutboxOpsController.java`, `NotificationOutboxOpsService`, `NotificationChannelSenderRegistry`, `idx_notification_outbox_timeline`, `src/main/java/com/kinderp/domain/notification/service/channel/*Sender.java` | `NotificationOutboxOpsApiIntegrationTest`, `NotificationOutboxPerformanceSmokeTest`, `ViewEndpointTest` | `/notification-outbox`, `README.md` 화면 섹션 | demo에서 outbox timeline의 status/channel/q 필터와 principal-only 테스트로 이동 |
| 외부 알림 provider는 sender adapter 경계 뒤에 둔다. | `NotificationChannelSender`, `NotificationChannelSenderRegistry`, `EmailNotificationSender`, `PushNotificationSender`, `IncidentWebhookNotificationSender`, `AppNotificationSender` | `NotificationChannelSenderRegistryTest`, `NotificationOutboxOpsApiIntegrationTest`, notification outbox integration tests | `docs/guides/risk-response.md`, `docs/guides/interview-guide.md` | 실제 provider 미연동 질문에서 adapter 경계와 운영 전 보완책을 함께 설명 |
| 감사 로그는 세부 필터와 CSV export로 운영 조사에 쓸 수 있다. | `AuthAuditLogController`, `AuthAuditLogQueryService`, `DomainAuditLogController`, `DomainAuditLogQueryService` | `AuthAuditApiIntegrationTest`, `DomainAuditApiIntegrationTest`, `AuditConsolePerformanceSmokeTest` | `/audit-logs`, `/domain-audit-logs`, README API 표 | reason/summary 필터와 CSV export가 같은 조건을 쓰는 점을 설명 |
| Swagger/OpenAPI와 app-port Prometheus는 기본 공개가 아니라 opt-in이다. | `src/main/java/com/kinderp/global/config/SecurityConfig.java`, `src/main/java/com/kinderp/global/config/StartupSafetyValidator.java`, `src/main/java/com/kinderp/global/monitoring/PrometheusScrapeController.java` | `src/test/java/com/kinderp/integration/ObservabilityIntegrationTest.java`, `src/test/java/com/kinderp/integration/ManagementSurfaceOptInIntegrationTest.java`, `src/test/java/com/kinderp/global/config/StartupSafetyValidatorTest.java` | `docs/guides/env-contract.md` | 보안/운영 질문에서 default deny와 explicit opt-in 테스트를 제시 |
| prod에서는 seed, Swagger, app-port Prometheus, 약한 JWT secret, insecure cookie, wildcard/non-HTTPS CORS를 막는다. | `src/main/java/com/kinderp/global/config/StartupSafetyValidator.java`, `src/main/java/com/kinderp/global/security/CorsProperties.java`, `src/main/resources/application-prod.yml` | `src/test/java/com/kinderp/global/config/StartupSafetyValidatorTest.java` | `docs/guides/env-contract.md`, `docs/guides/deployment-guide.md` | prod safety 질문에서 validator와 env contract를 함께 제시 |
| Credentialed CORS가 명시 origin과 필요한 요청 헤더만 허용한다. | `SecurityConfig.corsConfigurationSource`, `CorsProperties` | `SecurityCorsConfigTest`에서 origin/credentials/wildcard header 계약 검증 | `docs/guides/env-contract.md`, `docs/guides/risk-response.md` | CSRF·멱등 키·HTMX 요청은 유지하고 임의 헤더 확장은 막는다 |
| SSR 응답은 nonce 기반 CSP를 사용하고 외부 CDN/Google Fonts origin을 허용하지 않는다. | `src/main/java/com/kinderp/global/security/ContentSecurityPolicyFilter.java`, 로컬 `static/vendor/**` | `ContentSecurityPolicyFilterTest`, 외부 asset origin 정적 검색 | `docs/guides/production-like-checklist.md` | 로컬 자산 번들링과 CSP 허용 범위를 일치시킨다 |
| JWT와 CSRF 쿠키의 보안 속성이 환경별 계약과 일치한다. | `SecurityConfig.csrfTokenRepository`, `AuthService`, `JwtProperties` | `ViewEndpointTest`의 `XSRF-TOKEN` SameSite 검증, Auth 통합 테스트의 JWT 수명주기 검증 | `docs/guides/env-contract.md` | prod Secure/Strict 정책과 local HTTP 개발 편의를 함께 설명 |
| 웹 로그아웃도 Redis refresh session을 폐기한다. | `SecurityConfig` logout handler, `AuthService.logout` | `AuthApiIntegrationTest.webLogout_Success_RevokesRefreshSession`, `PageAccessIntegrationTest` | `docs/guides/deployment-guide.md` | 화면 로그아웃과 API 로그아웃의 세션 폐기 semantics를 일치시킨다 |
| 클라우드 미배포 상태에서도 production-like 기동·프록시·재시작 복구 증거가 있다. | `deploy/docker-compose.prod.yml`, `deploy/.env.prod.example`, `Dockerfile`, `deploy/Caddyfile` | 2026-08-14 Docker Desktop에서 readiness `200/UP`, HTTPS login `200`, HTTP→HTTPS `308`, 비인증 API `401`, 보안 헤더, graceful restart recovery 확인 | `docs/guides/production-like-checklist.md`, `docs/guides/deployment-guide.md` | 이는 과거 production-like 증거이며, 현재 netcup Compose·실제 도메인/TLS·내부 MySQL/Redis/provider는 별도 외부 실행이 필요하다고 명시 |
| 출결 변경 요청의 동시 재전송을 요청자 row lock과 DB unique key로 직렬화한다. | `AttendanceChangeRequestService`, `MemberRepository.findByIdWithKindergartenForUpdate`, V18 migration | `AttendanceChangeRequestApiIntegrationTest`, `WorkflowStateTransitionTest` | `README.md`, `docs/architecture/portfolio-story.md` | pending 중복과 Idempotency-Key race 방어를 함께 설명 |
| 배포 컨테이너의 자원·로그·종료 동작을 운영 기본값으로 제한했다. | `deploy/docker-compose.prod.yml`, `src/main/resources/application-prod.yml` | production-like compose config, compile/bootJar 검증 | `docs/guides/production-like-checklist.md` | resource cap, log rotation, graceful shutdown을 설명 |
| 관측성 metric/log 신호가 실제로 기록되도록 구성했다. | `src/main/java/com/kinderp/global/config/CacheConfig.java`, `src/main/resources/logback-spring.xml` | `ObservabilityIntegrationTest`, context startup | `docs/guides/production-like-checklist.md` | cache hit metric과 최신 rolling policy를 설명 |
| SSR 화면의 기본 접근성 계약을 자동 검사한다. | `scripts/check-template-accessibility.mjs`, `src/main/resources/templates/**` | `npm run accessibility:templates`, `ViewEndpointTest` | `docs/guides/developer-guide.md`, `.github/workflows/ci.yml` | label/ARIA 이름, 버튼 type, 이미지 alt 검사를 설명 |
| SSR 화면이 내부 예외 원문을 사용자에게 노출하지 않는다. | `src/main/java/com/kinderp/domain/*/controller/*ViewController.java`, `scripts/check-view-error-messages.mjs` | `npm run security:view-errors`, `compileJava` | `docs/guides/developer-guide.md`, `.github/workflows/ci.yml` | 사용자 메시지는 일반화하고 상세 원인은 서버 로그에만 남긴다 |
| Notepad/Dashboard 성능 개선은 수치로 검증했다. | `src/main/java/com/kinderp/domain/notepad/*`, `src/main/java/com/kinderp/domain/dashboard/*` | `src/test/java/com/kinderp/performance/*PerformanceStoryTest.java`, k6 결과 | `README.md`, `docs/architecture/performance-methodology.md` | query count/elapsed time과 제한된 HTTP p95를 구분해 설명 |
| CI는 혼자 운영하는 main 프로젝트에 맞춰 quick/heavy를 분리했다. | `.github/workflows/ci.yml`, `.github/workflows/backend-quality.yml`, `.github/workflows/cd.yml` | GitHub Actions run history | `README.md` 테스트 & CI | push CI는 빠른 실패 신호, heavy는 수동 검증이라고 설명 |
| 대표 업무 흐름이 tenant·상태 전이·감사·Outbox로 닫힌다. | `attendance`, `kidapplication`, `notification`, `global/security/access` | 관련 API 통합 테스트와 Outbox 테스트 | `docs/architecture/portfolio-story.md`, README 대표 업무 흐름 | 요청 생성부터 승인·실패 재시도까지 sequence diagram으로 먼저 설명 |

## 증거의 현재 한계

- 실제 클라우드 배포와 외부 provider sandbox는 아직 실행하지 않았으므로 “운영 중”이라고 표현하지 않는다.
- 동시성 정책은 repository lock과 상태 전이 테스트로 검증하고, 학부모 출결 변경 요청은 requester별 `Idempotency-Key`와 재사용 충돌 검증까지 제공한다. 외부 workflow 전체의 공통 멱등 키 표준화는 별도 범위다.
- 현재 성능 수치는 1,000건 규모 local fixture와 제한된 15 VU 로컬 시나리오의 근거다. 실제 운영 MySQL의 실행계획과 외부 HTTPS 환경 p95는 배포 후 추가 측정해야 한다.

## 2. 화면별 증거

| 화면 | 경로 | 보여주는 것 | 관련 API/테스트 |
| --- | --- | --- | --- |
| 원장 대시보드 | `/dashboard` | 통계, 캐시/집계 개선 스토리 | `/api/v1/dashboard/statistics`, dashboard performance tests |
| 입학 신청 큐 | `/applications/pending` | 승인, waitlist, offer 상태 전이 | kid application integration tests |
| 출결 요청 | `/attendance-requests` | 학부모 요청과 교사/원장 처리 경계 | attendance request integration tests |
| 인증 감사 로그 | `/audit-logs` | 로그인/refresh/security event reason filter/export | auth audit API/tests |
| 업무 감사 로그 | `/domain-audit-logs` | 상태 전이 summary filter/export | domain audit API/tests |
| 알림 Outbox 운영 | `/notification-outbox` | timeline/status/channel/search/dead-letter retry | `NotificationOutboxOpsApiIntegrationTest`, `ViewEndpointTest` |
| Swagger UI | `/swagger-ui.html` | local/demo API 계약 확인 | management surface opt-in tests |

## 3. 검증 명령 증거

| 목적 | 명령 | 쓰임 |
| --- | --- | --- |
| 빠른 컴파일 검증 | `./gradlew compileJava compileTestJava` | 작은 코드/annotation 변경 후 import/type 오류 확인 |
| release packaging | `./gradlew bootJar` | 면접/릴리스 직전 jar 패키징 확인 |
| push quick CI와 동일 계열 | `./gradlew fastTest` | unit/fast slice 중심의 빠른 실패 신호 |
| heavy 품질 검증 | `./gradlew integrationTest` | MySQL/Redis Testcontainers 기반 통합 검증 |
| 성능 smoke | `./gradlew performanceSmokeTest` | query count/elapsed time 회귀 확인 |
| GitHub 최신 CI | `gh run list --repo answndud/kinderp --branch main --limit 5` | 최신 main push 상태 확인 |

## 4. 주의할 점

- README에는 변동 가능한 최신 CI 시간을 고정값으로 남기지 않는다.
- 대표 개선값은 archive와 README에 남기되, 최신 상태는 GitHub Actions 배지와 run history를 기준으로 확인한다.
- 증거가 코드에 없거나 테스트가 없는 주장은 README에서 강하게 말하지 않는다.
