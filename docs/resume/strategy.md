# 토스뱅크 Server Developer 지원 전략

기준일: 2026-08-14

## 1. 공고 평가 기준 분석

토스뱅크 Server Developer 채용 연계형 인턴십은 Java/Kotlin과 Spring 기반 개발 경험, 확장 가능한 시스템에 대한 관심과 설계·구현 경험, 성능 최적화·자동화 경험, 요구사항을 데이터 모델/API로 구체화하는 능력을 확인합니다. 따라서 이 포트폴리오는 AI 활용 자체보다 문제 정의, 설계 선택, 수치 기반 검증, 운영 안전장치를 중심으로 설명합니다.

| 평가 기준 | 공고가 실제로 확인하려는 것 | 제출에서 보여줄 방식 |
| --- | --- | --- |
| 문제 해결력 | 요구사항을 그대로 구현하지 않고, 본질적 문제로 다시 정의하는가 | 유치원 ERP를 CRUD가 아니라 role/tenant, 상태 전이, 감사, 알림 실패 대응 문제로 재구성한 과정 |
| 실행력 | 아이디어가 아니라 작동하는 결과물을 만들었는가 | Spring Boot 3.5.14, Java 21, MySQL, Redis, Flyway, QueryDSL 기반 저장소와 테스트/CI 링크 |
| 성장·문제 해결 방식 | 낯선 요구사항을 학습하고 스스로 쪼개 개선하는가 | AI를 보조 수단으로 활용하되 설계 선택과 검증은 직접 책임진 과정 |
| 복잡한 요구사항 구조화 | 고객/현장 요구를 기술 문제로 나누고 우선순위를 정하는가 | 원장/교사/학부모 권한, 입학 신청 review workflow, outbox 운영 API, audit 조사 흐름 |
| 검증 책임 | AI가 낸 답을 그대로 믿지 않고 직접 확인하는가 | Testcontainers 통합 테스트, performance smoke, CI quick/heavy 분리, production-like checklist |
| 협업/설명력 | 본인이 만든 것을 타인이 이해하고 검증할 수 있게 설명하는가 | README, evidence map, interview guide, demo scenario, risk response, DONE archive |
| 한계 인식 | 약점을 숨기지 않고 다음 보완책을 말할 수 있는가 | 클라우드 미배포, 외부 provider 미연동을 명시하고 운영 전 보완책 제시 |

## 2. 프로젝트 강점 매핑

| 프로젝트 강점 | 공고 기준과 연결 | 실제 증거 |
| --- | --- | --- |
| 단순 CRUD를 운영형 백엔드 문제로 확장 | 문제 재정의, 복잡한 요구사항 구조화 | [`../../README.md`](../../README.md), [`../guides/evidence-map.md`](../guides/evidence-map.md) |
| role/tenant 권한 경계 | 보안 감각, 요구사항 분해, 검증 책임 | [`../../src/main/java/com/kinderp/global/security/access`](../../src/main/java/com/kinderp/global/security/access), [`../../src/test/java/com/kinderp/api`](../../src/test/java/com/kinderp/api) |
| Cookie JWT + Redis refresh/session registry | 인증/세션 설계, 운영상 revoke 요구 반영 | [`../../src/main/java/com/kinderp/global/security/jwt`](../../src/main/java/com/kinderp/global/security/jwt), [`../../src/main/java/com/kinderp/domain/auth`](../../src/main/java/com/kinderp/domain/auth) |
| 입학 신청 review workflow 분리 | 상태 전이와 side effect를 분리하는 설계 | [`../../src/main/java/com/kinderp/domain/kidapplication/service`](../../src/main/java/com/kinderp/domain/kidapplication/service), [`../../src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java`](../../src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java) |
| Notification outbox timeline/dead-letter/retry | 외부 시스템 실패를 운영 가능한 흐름으로 설계 | [`../../src/main/java/com/kinderp/domain/notification`](../../src/main/java/com/kinderp/domain/notification), [`../../src/test/java/com/kinderp/api/NotificationOutboxOpsApiIntegrationTest.java`](../../src/test/java/com/kinderp/api/NotificationOutboxOpsApiIntegrationTest.java) |
| Audit log 필터/export | 장애/문의 대응을 위한 운영 조사 가능성 | [`../../src/main/java/com/kinderp/domain/authaudit`](../../src/main/java/com/kinderp/domain/authaudit), [`../../src/main/java/com/kinderp/domain/domainaudit`](../../src/main/java/com/kinderp/domain/domainaudit) |
| 성능 개선 수치 | "개선했다"를 숫자로 검증 | Notepad `22 -> 5` queries, Dashboard cache hit `5 -> 0` queries, [`../../src/test/java/com/kinderp/performance`](../../src/test/java/com/kinderp/performance) |
| CI 경량화 | 혼자 운영하는 main 프로젝트에 맞는 실용적 자동화 | [`../../.github/workflows/ci.yml`](../../.github/workflows/ci.yml), [`../../.github/workflows/backend-quality.yml`](../../.github/workflows/backend-quality.yml) |
| Production-like safety | 실제 배포 전 리스크를 fail-closed로 차단 | [`../guides/production-like-checklist.md`](../guides/production-like-checklist.md), [`../../src/main/java/com/kinderp/global/config`](../../src/main/java/com/kinderp/global/config), [`../../deploy`](../../deploy) |
| 문서 기반 작업 운영 | 비즈니스 분석부터 기술 명세·개발·운영까지 설명 가능한가 | [`../../PLAN.md`](../../PLAN.md), [`../guides/evidence-map.md`](../guides/evidence-map.md), [`../../AGENTS.md`](../../AGENTS.md) |

## 2-1. 희망 조직과의 연결

현재 프로젝트의 강점은 Product Foundation과 Platform Engineering의 문제에 가장 가깝습니다.

- Product Foundation: 역할·tenant 경계가 있는 내부 운영 시스템, 감사 로그, 승인 workflow, 운영 화면을 구현했습니다.
- Platform Engineering: Redis 기반 인증 상태 관리, rate limit, outbox 재처리, readiness/graceful shutdown, 백업·롤백 절차와 성능 측정 체계를 준비했습니다.

Loan이나 Customer Asset 도메인의 금융 업무 경험이 있다고 주장하지 않습니다. 대신 복잡한 업무 규칙을 상태 전이와 API 계약으로 구조화하고, 실패·재시도·권한 경계를 검증한 경험을 서버 개발 역량의 증거로 제시합니다.

## 3. 제출에서 밀어야 할 핵심 사례 3개

### 사례 1. 권한/세션/운영 가드를 함께 닫은 보안 경계

핵심 메시지는 "로그인 기능을 만든 것"이 아니라 "역할, tenant, 세션 revoke, production profile 위험 설정까지 한 흐름으로 닫았다"입니다.

- 원장, 교사, 학부모 역할별 접근 권한을 API와 service 경계에서 함께 검증합니다.
- JWT는 HTTP-only cookie 기반으로 두고, refresh/session revoke는 Redis TTL과 active session registry로 관리합니다.
- prod에서는 seed, Swagger/OpenAPI, app-port Prometheus, insecure cookie, wildcard/non-HTTPS CORS 같은 위험 설정을 fail-closed로 차단합니다.
- 제출 문장에서는 "보안 기능을 구현했다"보다 "운영에서 문제가 되는 노출면을 먼저 정의하고 테스트로 막았다"를 강조합니다.

### 사례 2. 외부 실패를 outbox 운영 흐름으로 재구성

핵심 메시지는 "알림을 보냈다"가 아니라 "외부 provider가 실패할 수 있다는 전제에서 관측, 검색, 재시도, adapter 교체 경계를 설계했다"입니다.

- `NotificationOutbox`는 retry/backoff/dead-letter 상태를 갖습니다.
- 원장 전용 outbox 운영 API와 화면에서 timeline, status/channel/search 필터, dead-letter retry를 제공합니다.
- `NotificationChannelSenderRegistry`로 channel별 sender adapter를 분리해 provider 교체 지점을 명확히 했습니다.
- 실제 외부 provider 발송은 아직 운영 검증 대상이 아니므로, "실제 발송 운영"이라고 표현하지 않습니다.

### 사례 3. 성능/CI/문서화를 숫자와 근거로 남긴 개선 루프

핵심 메시지는 "리팩토링을 많이 했다"가 아니라 "개선 전후를 수치로 남기고, 혼자 운영 가능한 검증 비용으로 정리했다"입니다.

- Notepad 조회는 query count `22 -> 5`로 줄였습니다.
- Dashboard cache hit은 query count `5 -> 0`으로 줄였습니다.
- Backend CI는 `5m 28s` 수준에서 1분대 quick check 중심으로 경량화했습니다.
- performance smoke, CI quick/heavy 분리, README/evidence map/DONE archive로 결과를 추적 가능하게 남겼습니다.

## 4. 약점과 방어 논리

| 약점 | 숨기면 안 되는 사실 | 방어 논리 | 제출 표현 |
| --- | --- | --- | --- |
| 클라우드 미배포 | 실제 운영 서버는 없다 | 비용 제약을 인정하되, Dockerfile, prod compose, env contract, startup safety, readiness/checklist로 배포 전 검증 가능성을 확보했다 | "실제 클라우드 운영은 아직 하지 않았지만, production-like 배포 자산과 안전장치를 준비했습니다." |
| 외부 알림 provider 미운영 | 실제 provider 계정 기반 발송은 제한적이다 | provider보다 중요한 outbox 상태 전이, dead-letter 관측, retry 운영면을 먼저 검증했다 | "provider adapter 경계와 outbox 운영 흐름을 먼저 닫았습니다." |
| Frontend asset 운영 | 저장소 로컬 vendor asset과 Tailwind build 산출물을 사용한다 | CDN 의존성을 제거하고 `frontend:build`로 재현 가능한 asset 생성 경로를 만들었다 | "SSR 운영 화면이지만 asset은 로컬 build로 고정했고, 외부 provider는 별도 검증 대상으로 분리했습니다." |
| solo project | 실제 팀 협업 경험은 제한적이다 | README, evidence map, interview guide, risk response, AGENTS, PLAN/DONE으로 타인이 검증 가능한 맥락을 남겼다 | "협업 대신 문서/테스트/CI로 검증 가능한 인수인계 단위를 만들었습니다." |
| 도메인 규모 | 실제 유치원 고객 검증은 없다 | 도메인은 포트폴리오 시나리오이며, 백엔드 문제의 복잡도를 보여주기 위해 선택했다 | "실제 고객 운영 사례가 아니라 운영형 백엔드 설계 능력을 보여주는 프로젝트입니다." |

## 5. 제출 전 보강하면 좋은 포인트

1. GitHub Actions 최신 성공 run 링크를 체크리스트에 붙입니다.
2. README 상단 스크린샷이 현재 `/applications/pending`, `/notification-outbox`, audit 화면과 일치하는지 확인합니다.
3. 지원서에는 클라우드 미배포를 숨기지 말고, production-like checklist와 risk response 링크를 함께 제시합니다.
4. 가능하면 2분 이하 데모 영상을 녹화해 README, outbox 화면, audit 화면, test/CI 증거를 순서대로 보여줍니다.
5. `application-draft.md`의 개인정보, GitHub, 연락처, 최신 commit hash를 제출 직전에 채웁니다.
6. 면접 전에는 [`interview-playbook.md`](interview-playbook.md)의 공격 질문 답변을 말로 1회 이상 연습합니다.
