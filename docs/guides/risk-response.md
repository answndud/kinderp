# Risk Response

기준일: 2026-08-14

이 문서는 면접관이 약점을 찾는 관점에서 물어볼 수 있는 질문에 대해, 숨기지 않고 현재 판단과 운영 전 보완책을 설명하기 위한 문서입니다.

## 1. 약점 질문 대응표

| 질문/약점 | 현재 상태 | 왜 이렇게 했나 | 운영 전 보완책 | 근거 |
| --- | --- | --- | --- | --- |
| 왜 실제 클라우드 배포가 없나요? | 클라우드 미배포, Dockerfile/deploy 자산/runbook만 준비 | 비용 제약이 있고, 취업 포트폴리오에서는 로컬 재현성과 배포 준비도를 우선했다 | 도메인, HTTPS, secret, DB backup, rollback, health/readiness를 실제 서버에서 검증 | `deploy/*`, `docs/guides/deployment-guide.md`, `docs/guides/env-contract.md` |
| Tailwind asset을 production처럼 관리할 수 있나요? | Thymeleaf + HTMX + Alpine.js + 저장소 로컬 vendor asset + Tailwind build 산출물 | 초기 CDN 의존성을 제거하고 frontend asset을 재현 가능한 build 입력으로 고정했다 | 외부 asset 추가 시 공식 digest/SRI 확인, bundle size를 계속 점검 | `package.json`, `scripts/build-assets.mjs`, `tailwind.config.js`, `.impeccable.md` |
| 모놀리식 구조가 과하지 않거나 반대로 너무 단순하지 않나요? | Spring Boot monolith, `domain/*`와 `global/*` 분리 | 유치원 ERP 규모에서는 트랜잭션/권한/운영 흐름을 한 저장소에서 닫는 편이 설명 가능성이 높다 | 트래픽/조직 규모가 커지면 notification/audit/reporting 같은 비동기/읽기 영역부터 분리 | `src/main/java/com/kinderp/domain`, `src/main/java/com/kinderp/global` |
| 외부 알림은 실제 발송이 아니라 시연/mocking 중심 아닌가요? | sender adapter 경계, outbox 상태 전이, retry/backoff/dead-letter, timeline/search/filter 운영 API와 Prometheus 발송 결과 메트릭, 로컬 통합 테스트의 실제 payload HMAC 계약 검증 | 외부 provider 비용/계정 없이도 실패 대응·재처리·서명 생성 계약을 검증하기 위함 | 실제 provider credential, provider 응답/서명 검증, DLQ 알림, provider별 rate limit과 sandbox smoke 추가 | `NotificationDispatchService`, `NotificationChannelSender`, `AuthAnomalyIncidentChannelIntegrationTest` |
| OAuth2는 실제 운영 redirect까지 검증했나요? | Google/Kakao 설정 경로와 local/demo 중심 | 실제 client secret과 운영 도메인이 없으므로 로컬 설정 계약까지만 준비 | 운영 도메인 발급 후 redirect URI, secure cookie, SameSite, CORS를 함께 검증 | `docs/guides/env-contract.md`, `SecurityConfig` |
| demo seed가 prod에 켜질 위험은 없나요? | base default off, demo explicit on, prod validator로 seed 차단 | demo 재현성과 prod fail-closed를 분리했다 | 배포 pipeline에서 `SPRING_PROFILES_ACTIVE=prod`와 secret 검증을 강제 | `StartupSafetyValidator`, `StartupSafetyValidatorTest` |
| Swagger/Prometheus가 외부 공개될 위험은 없나요? | 기본 비공개, local/demo opt-in, prod validator 차단 | 운영면은 필요하지만 기본 공개는 위험하므로 opt-in으로 분리했다 | reverse proxy와 management plane 분리, IP allowlist, auth 적용 | `SecurityConfig`, `ObservabilityIntegrationTest`, `ManagementSurfaceOptInIntegrationTest` |
| CORS 설정을 잘못 열 위험은 없나요? | prod validator가 wildcard와 non-HTTPS origin을 차단 | cookie 인증을 쓰므로 credentialed CORS에서 origin을 좁히는 것이 필수다 | 실제 도메인 확보 후 `CORS_ALLOWED_ORIGINS=https://...`만 설정 | `CorsProperties`, `StartupSafetyValidatorTest`, `env-contract.md` |
| full test를 매번 돌리지 않는 건 신뢰도 문제가 아닌가요? | push CI는 quick, heavy suite는 수동 workflow | 혼자 운영하는 main 포트폴리오에서 비용/시간과 빠른 피드백을 균형화했다 | 큰 기능/보안/DB 변경 후 `Backend Quality` 수동 실행을 release gate로 사용 | `.github/workflows/ci.yml`, `.github/workflows/backend-quality.yml` |

## 2. Red-Team 최소 점검

| 점검 항목 | 현재 판정 | 근거 | 남은 리스크 |
| --- | --- | --- | --- |
| prod에서 seed가 켜질 수 있는가 | 낮음 | `StartupSafetyValidator`가 prod + `app.seed.enabled=true`를 차단, 테스트 존재 | 실제 CD secret/env 오입력은 workflow/서버에서 추가 검증 필요 |
| prod에서 Swagger/OpenAPI가 공개될 수 있는가 | 낮음 | `StartupSafetyValidator`, `SecurityConfig`, observability/management opt-in tests | reverse proxy rule이 별도로 생기면 별도 점검 필요 |
| prod에서 app-port Prometheus가 공개될 수 있는가 | 낮음 | `app.security.management-surface.expose-prometheus-on-app-port=false` 기본, opt-in test 존재 | 실제 운영에서는 management port/IP 제한 필요 |
| prod에서 credentialed CORS가 과하게 열릴 수 있는가 | 낮음 | `StartupSafetyValidator`가 wildcard/non-HTTPS origin을 차단 | 실제 도메인 변경 시 env와 OAuth redirect URI를 함께 바꿔야 함 |
| outbox 운영 API를 교사가 호출할 수 있는가 | 낮음 | `NotificationOutboxOpsApiIntegrationTest.teacherCannotAccessOutboxOps` | 새 endpoint 추가 시 SecurityConfig와 `@PreAuthorize` 동시 점검 필요 |
| outbox 화면을 교사가 열 수 있는가 | 낮음 | `ViewEndpointTest.testNotificationOutboxPageForTeacherForbidden` | 프론트 링크 숨김만으로 판단하지 말고 서버 권한 테스트 유지 필요 |
| README CI 최신 시간이 낡을 수 있는가 | 완화됨 | README에서 고정 최신값 제거, 배지/Actions 기준으로 변경 | 대표 개선값은 archive와 실제 run history가 어긋나면 재측정 필요 |
| 클라우드 미배포가 약점으로 보일 수 있는가 | 중간 | README와 이 문서에서 미배포를 명시 | 비용 문제가 해결되면 최소 1회 실제 배포 smoke가 가장 강한 보완책 |
| CI가 잘못된 배포 서버로 SSH 연결할 수 있는가 | 낮음 | CD가 운영 검증 후 저장한 `DEPLOY_KNOWN_HOSTS`만 사용하고 `StrictHostKeyChecking=yes`를 강제 | 서버 교체 시 fingerprint 확인 후 secret을 함께 갱신 |

## 3. 면접 답변 원칙

- 약점을 숨기지 말고 "현재 범위", "왜 이 판단을 했는지", "운영 전 무엇을 해야 하는지" 순서로 답한다.
- 비용 문제로 미배포인 점은 인정하되, 배포 자산, 환경 계약, readiness/health, CI/CD 분리까지 준비했다는 점을 보여준다.
- 기능이 있다는 주장보다 해당 기능을 검증하는 테스트와 실패 시 운영 대응 경로를 먼저 보여준다.
- "완벽한 운영 시스템"이 아니라 "신입 백엔드 개발자가 운영형 문제를 어디까지 고려했는지 보여주는 포트폴리오"라는 범위를 유지한다.
