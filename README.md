# Kindergarten ERP

> 유치원 운영 ERP를 주제로, 인증/권한/상태 전이/감사/관측성을 끝까지 다룬 Spring Boot 백엔드 포트폴리오입니다.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![Backend CI](https://github.com/answndud/Kindergarten_ERP/actions/workflows/ci.yml/badge.svg)](https://github.com/answndud/Kindergarten_ERP/actions/workflows/ci.yml)

## 30초 요약

- 이 프로젝트의 핵심 문제는 **하나의 유치원 데이터를 원장·교사·학부모가 서로 다른 권한으로 동시에 처리할 때 정합성과 운영 추적성을 어떻게 보장할 것인가**입니다.
- 단순 CRUD가 아니라 **tenant 경계, 입학·출결 승인 상태 전이, 감사 로그, Outbox dead-letter 운영**까지 하나의 업무 흐름으로 닫았습니다.
- 성능 개선은 감으로 처리하지 않고 **쿼리 수/응답 시간/CI 시간**을 전후 비교했습니다. Notepad 목록은 `22 queries -> 5 queries`, Dashboard 반복 조회는 cache hit 기준 `5 queries -> 0 queries`로 줄였습니다.
- 면접 시연은 `demo` 프로파일로 재현 가능합니다. 최신 main 기준 `Backend CI` 상태는 상단 배지와 [Actions](https://github.com/answndud/Kindergarten_ERP/actions/workflows/ci.yml)에서 확인합니다.
- 실제 클라우드 배포는 비용 문제로 수행하지 않았고, 대신 Docker/배포 자산/runbook과 local/demo/prod 환경 계약을 분리해 설명 가능하게 준비했습니다.

> TownPet이 레거시 이관과 서비스 구조의 안전성을 보여준다면, Kindergarten ERP는 운영 중 발생하는 권한·승인·동시성·실패 복구를 통제하는 내부 플랫폼을 보여줍니다. 상세 설계는 [포트폴리오 설계 서사](./docs/architecture/portfolio-story.md)에서 확인할 수 있습니다.

## 한눈에 보기

| 항목 | 내용 |
|------|------|
| 프로젝트 성격 | 다중 테넌트 내부 운영 플랫폼 포트폴리오 |
| 포트폴리오에서 강조한 역량 | 권한·workflow 정합성, 동시성, 실패 복구, 성능 측정, 운영 관측성 |
| 핵심 사용자 | `PRINCIPAL`, `TEACHER`, `PARENT` |
| 핵심 기술 | Java 21, Spring Boot 3.5.9, MySQL 8, Redis, JPA, QueryDSL |
| 실행 프로필 | `local`, `demo`, `prod` |
| 최근 운영 개선 | 입력 오류 500 방지, 인증 rate-limit/`Retry-After`, graceful shutdown, production Compose resource/log guardrail, Notification Outbox 운영면, `Backend CI` `5m 28s -> 1분대` |
| 바로 볼 문서 | [`PLAN.md`](./PLAN.md), [`docs/guides/developer-guide.md`](./docs/guides/developer-guide.md), [`docs/guides/env-contract.md`](./docs/guides/env-contract.md), [`docs/guides/deployment-guide.md`](./docs/guides/deployment-guide.md) |
| 최소 로컬 검증 | 빠른 수정은 `./gradlew compileJava compileTestJava` + `git diff --check`, 릴리스 전만 `./gradlew test` |

## 3분 리뷰 루트

면접관이 짧게 훑는다면 아래 5곳만 보면 됩니다.

1. [핵심 문제와 해결](#핵심-문제와-해결): tenant, 권한, 상태 전이, 감사, outbox 실패 대응.
2. [수치로 검증한 개선](#수치로-검증한-개선): 쿼리 수, 응답 시간, CI 시간 개선.
3. [화면](#화면): 대시보드, 신청 큐, 출석, 감사 로그, outbox 운영 화면.
4. [API / 운영 문서](#api--운영-문서): Swagger, audit export, outbox, dashboard API.
5. [테스트 & CI](#테스트--ci): quick CI와 manual quality 분리 이유.

## 대표 업무 흐름

학부모가 출결 변경을 요청하면 API는 세션과 tenant 경계를 확인한 뒤 요청을 저장합니다. 네트워크 재전송은 `Idempotency-Key`로 같은 요청 ID를 재사용하고, 다른 payload의 키 재사용은 거부합니다. 교사 또는 원장이 이를 승인하면 허용된 상태 전이와 동시성 조건을 검증하고, 업무 감사 로그와 알림 Outbox를 남깁니다. 외부 전달이 실패하면 worker는 해당 건을 dead-letter로 전환하고, 원장은 실패 원인·시도 횟수·재시도 이력을 확인합니다.

이 흐름은 [아키텍처 및 증거 지도](./docs/architecture/portfolio-story.md)의 대표 시나리오와 코드·테스트·운영 화면을 연결합니다.

## 바로 확인할 것

- [5분 실행 / 검증](#5분-실행--검증): `demo` 프로파일과 시연 계정으로 빠르게 재현할 수 있습니다.
- [수치로 검증한 개선](#수치로-검증한-개선): 쿼리 수와 응답 시간 기준 개선 결과를 먼저 볼 수 있습니다.
- [화면](#화면): 대시보드, 신청 처리 큐, 감사 로그, 알림 Outbox 운영 화면을 바로 확인할 수 있습니다.
- [PLAN.md](./PLAN.md): 현재/향후 구현 작업과 검증 계획을 확인할 수 있습니다.
- [docs/guides/evidence-map.md](./docs/guides/evidence-map.md): README의 주요 주장별 코드/테스트/문서 증거를 연결했습니다.
- [docs/guides/risk-response.md](./docs/guides/risk-response.md): 미배포, 모놀리식, 외부 provider 미연동, demo/mock 범위 같은 약점 질문 대응을 정리했습니다.
- [docs/guides/production-like-checklist.md](./docs/guides/production-like-checklist.md): cloud 미배포 상태에서 반복 가능한 prod safety/bootJar/compose dry-run 증거를 정리했습니다.
- [docs/guides/interview-guide.md](./docs/guides/interview-guide.md): 면접관 관점에서 볼 핵심 개선 스토리와 질문 대응 포인트를 정리했습니다.
- [docs/guides/demo-scenario.md](./docs/guides/demo-scenario.md): demo 계정, 클릭 순서, 실패 시 복구 절차를 정리했습니다.

## 왜 이 저장소를 열어볼 만한가

- 단순 CRUD가 아니라 tenant 권한 경계, 세션 수명주기, 승인 워크플로우, 감사 로그 같은 운영형 백엔드 문제를 다뤘습니다.
- 기능을 추가하는 데서 멈추지 않고 Testcontainers, CI 분리, Prometheus/Grafana, structured logging까지 연결했습니다.
- 성능 작업은 "느린 지점을 찾고, 수치로 검증하고, 개선 후 다시 측정"하는 방식으로 정리했습니다.
- 학부모, 교사, 원장이 실제로 상호작용하는 서비스 흐름과 운영 도구가 한 저장소 안에서 닫히는 구조입니다.

## 제출 전 상태

| 항목 | 상태 |
|------|------|
| Main branch | `main` 고정 운영 |
| 최신 CI | `Backend CI` 배지와 Actions에서 확인. 최근 main push는 1분대 통과 |
| Demo smoke | `/dashboard`, `/applications/pending`, `/notification-outbox`, `/swagger-ui.html` 확인 |
| Release check | `./gradlew bootJar` 통과 |
| Prod safety | seed, Swagger/OpenAPI, app-port Prometheus, insecure cookie, wildcard/non-HTTPS CORS 차단 테스트 보유 |
| Production-like dry-run | bootJar, local/prod compose config, backup/checksum 및 disposable MySQL/Redis restore drill 통과 |
| 배포 | 클라우드 미배포. `deploy/*`, Dockerfile, 배포 가이드만 준비 |

## 핵심 문제와 해결

| 문제 | 적용한 방식 | 확인 포인트 |
|------|-------------|-------------|
| 멀티테넌시 권한 경계 | 역할 기반 인가 + `kindergarten_id` 기준 접근 제어 + fail-closed 기본값 유지 | principal/teacher/parent 권한 차등, 감사 로그 tenant 필터 |
| JWT 세션 수명주기 | HTTP-only cookie JWT + Redis refresh session rotation + 활성 세션 레지스트리 | 세션 조회, 개별 종료, 다른 기기 로그아웃, 즉시 revoke |
| 운영형 상태 전이 | waitlist/offer/offer expiry, 출결 변경 요청 승인/거절, 공지/알림 워크플로우 | 승인 상태 전이, scheduler, domain audit log |
| 운영 가시성 부족 | auth audit log, domain audit log, management plane, Prometheus/Grafana, structured logging | reason/summary 필터, 조회/export API, 운영 화면, readiness/metrics |
| 테스트 신뢰성 부족 | MySQL/Redis Testcontainers 통합 테스트 + `fast/integration/performanceSmoke` CI 분리 | GitHub Actions 배지, 테스트 태스크, smoke 검증 |
| 운영 실패 대응 부족 | Notification Outbox timeline, status/channel/search filter, dead-letter retry API 추가 | `/api/v1/notification-outbox/*`, principal-only 통합 테스트 |
| 인증 남용과 재시도 혼선 | Redis 기반 login/refresh rate limit, 429 `Retry-After` 계약 | `AuthApiIntegrationTest`, `AuthRateLimitService` |
| 배포 컨테이너 운영 위험 | production Compose 자원 상한, 로그 rotation, `no-new-privileges`, graceful shutdown | `deploy/docker-compose.prod.yml`, `application-prod.yml`, production-like checklist |
| 입력 오류 500 위험 | MVC parameter/type/date 예외를 400 `ApiResponse.error`로 정규화 | 출석 월 조회 invalid/missing/type 오류 테스트 |
| 과도한 일정 조회 | 캘린더 조회 기간 366일 cap + `RecurrenceExpander` 분리 | fast unit test, calendar integration test |

## 수치로 검증한 개선

| 대상 | 개선 전 | 개선 후 | 핵심 개선 |
|------|--------:|--------:|----------|
| Notepad 목록 조회 | queries 22, 17ms | queries 5, 6ms | 읽음 수 N+1 제거, 다건 집계 쿼리 전환 |
| Dashboard 통계 | queries 13, 13ms | queries 5, 5ms | 정확도 보정 + 집계 쿼리 통합 |
| Dashboard 반복 조회 | queries 5, 12ms | queries 0, 0ms | 60초 TTL 캐시 적용 (`dashboardStatistics`) |
| Backend CI wall-clock | 5m 28s | 1m 14s 대표, 최근 main push 1분대 | push CI는 quick check로 축소, heavy 검증은 수동 workflow로 분리 |

- k6 부하 테스트 결과 (2026-08-14, Docker k6, 15 VU, 각 30초)
  - Notepad list: p95 69.36ms, p99 99.52ms, error 0.00%
  - Dashboard stats: p95 25.39ms, p99 29.96ms, error 0.00%
  - 전체 `http_req_duration`: p95 362.13ms, p99 464.86ms, error 0.00%
- 측정 조건, 수치의 범위, 아직 없는 증거는 [성능 측정 방법과 증거 범위](./docs/architecture/performance-methodology.md)에 명시했습니다.

## 화면

2026-08-14 기준 운영형 responsive 화면입니다. 원장·교사 업무 큐와 학부모 모바일 핵심 흐름을 함께 검증했습니다.

| 원장 대시보드 | 출석 관리 |
|---|---|
| ![원장 대시보드](./docs/assets/readme/dashboard-desktop.png) | ![출석 관리](./docs/assets/readme/attendance-desktop.png) |

| 신청 처리 큐 | 알림장 |
|---|---|
| ![신청 처리 큐](./docs/assets/readme/applications-pending-desktop.png) | ![알림장](./docs/assets/readme/notepad-desktop.png) |

| 인증 감사 로그 | 업무 감사 로그 |
|---|---|
| ![인증 감사 로그](./docs/assets/readme/audit-desktop.png) | ![업무 감사 로그](./docs/assets/readme/domain-audit-desktop.png) |

| 알림 Outbox 운영 |
|---|
| ![알림 Outbox 운영](./docs/assets/readme/outbox-desktop.png) |

## 서비스가 실제로 어떻게 닫히는가

1. 학부모는 입학 신청이나 출결 변경 요청을 만들고, 시스템은 이를 tenant 경계 안에서 저장합니다.
2. 교사와 원장은 반 정원, 승인 대기 큐, 출석, 알림장, 공지, 일정 같은 운영 업무를 처리합니다.
3. 인증 이벤트는 `auth audit log`, 업무 상태 전이는 `domain audit log`에 기록되고 export API로 이어집니다.
4. 원장은 대시보드, 시스템 알림, 활성 세션 제어, Prometheus/Grafana를 통해 운영 상태를 확인합니다.

## 대표 기능

### 원장

- 교사 지원 승인/거절, 학부모 입학 신청 승인, waitlist, offer 발행, offer expiry 관리
- 인증 감사 로그와 업무 감사 로그 조회 및 CSV export
- 활성 세션 조회, 개별 세션 종료, 다른 기기 로그아웃
- 출석/회원/공지 지표 기반 대시보드 확인
- 로그인 이상 징후 시스템 알림 확인

### 교사

- 일별 출석 체크, 등원/하원/결석 처리, 월간 리포트 조회
- 학부모 출결 변경 요청 승인/거절
- 알림장 작성, 공지 작성, 일정 관리
- 반 정원과 배정 상태를 고려한 원생 운영

### 학부모

- 원생 입학 신청, offer 수락, 출결 변경 요청 생성/취소
- 원생별 알림장 확인 및 읽음 처리
- 내 원생 정보와 출석 상태 조회

### 공통 백엔드 기능

- Google/Kakao OAuth2 로그인, 명시적 소셜 계정 연결, provider 충돌 정책
- signup/login/refresh rate limit, 429 `Retry-After`, trusted proxy 기반 client IP 해석
- `notification_outbox` 기반 비동기 알림 전달과 retry/backoff/dead-letter 처리
- 원장 전용 outbox 운영 API(timeline, status/channel/search filter, dead-letter retry)
- auth audit/domain audit archive-purge scheduler

## 아키텍처 요약

- Spring Boot 모놀리식 구조이지만 `domain/*`와 `global/*`로 역할을 분리했습니다.
- JPA + QueryDSL을 사용하고, OSIV는 `OFF`로 유지합니다.
- 인증은 JWT를 HTTP-only cookie로 전달하고, refresh session은 Redis TTL로 관리합니다.
- 활성 access token은 Redis 세션 레지스트리에 연결해 로그아웃/세션 종료 시 즉시 revoke합니다.
- UI는 Thymeleaf + HTMX + Alpine.js 조합으로 SSR 중심으로 구성했습니다.
- 운영 관측성은 Actuator, Prometheus, Grafana, correlation id, structured logging까지 포함합니다.

```text
erp/
├── src/
│   ├── main/java/com/erp/
│   │   ├── global/                  # config, security, exception, common
│   │   └── domain/                  # auth, member, kid, attendance, notification...
│   ├── main/resources/
│   │   ├── application*.yml
│   │   └── db/migration/            # Flyway migration
│   └── test/                        # unit/integration/performance smoke tests
├── docker/                          # local infra + monitoring overlay
├── docs/
│   ├── README.md                    # 문서 시작점
│   ├── architecture/                # workflow, access, performance evidence
│   ├── resume/                      # hiring-facing application materials
│   ├── guides/                      # env/developer/user/deployment guide
│   └── assets/readme/               # README screenshots
└── blog/                            # 구현 배경과 설계 설명 글
```

## 5분 실행 / 검증

### 1. 저장소 클론

```bash
git clone https://github.com/answndud/Kindergarten_ERP.git
cd Kindergarten_ERP
```

### 2. 로컬 infra 실행

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
docker ps
```

### 3. 데모 프로파일 실행

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

| 역할 | 계정 |
|------|------|
| 원장 | `principal@test.com / test1234!` |
| 교사 | `teacher1@test.com / test1234!` |
| 학부모 | `parent1@test.com / test1234!` |

### 4. 시연 시 바로 볼 경로

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 출결 요청 화면: `http://localhost:8080/attendance-requests`
- 인증 감사 로그 화면: `http://localhost:8080/audit-logs`
- 업무 감사 로그 화면: `http://localhost:8080/domain-audit-logs`
- 알림 Outbox 운영 화면: `http://localhost:8080/notification-outbox`
- Prometheus scrape: `http://localhost:8080/actuator/prometheus`

### 5. 테스트 실행

```bash
./gradlew test
./gradlew fastTest
./gradlew integrationTest
./gradlew performanceSmokeTest
```

- 통합 테스트는 MySQL/Redis Testcontainers 기반입니다.
- 실행 전 필수 환경 변수는 [`docs/guides/env-contract.md`](./docs/guides/env-contract.md)를 확인하면 됩니다.

## 현재 상태

| 항목 | 상태 |
|------|------|
| Core backend MVP | 인증, 출석, 알림장, 공지, 지원/승인, 감사 로그, 대시보드까지 완료 |
| Demo | `demo` 프로파일과 seed 계정으로 로컬 시연 가능 |
| Verification | push quick CI + 수동 quality workflow + 로컬 `test`/`integrationTest`/`performanceSmokeTest` 구성 완료 |
| Operations | auth/domain audit, management plane, Prometheus/Grafana overlay, active session control 포함 |
| Deployment package | `Dockerfile`, `deploy/*`, [`docs/guides/deployment-guide.md`](./docs/guides/deployment-guide.md) 기준 배포 자산 정리 |
| Active work | [`PLAN.md`](./PLAN.md)에서 현재/향후 작업을 관리 |

## API / 운영 문서

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 위 경로는 `local`/`demo`에서만 열고, `prod`에서는 비활성화합니다.
- 전체 API 계약은 Swagger/OpenAPI를 기준으로 확인하는 것을 권장합니다.

| 영역 | 대표 엔드포인트 | 포인트 |
|------|------------------|--------|
| Auth | `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/sessions` | refresh rotation, active sessions |
| Member | `/api/v1/members/me`, `/api/v1/members/password` | 자기 정보/보안 설정 |
| Kid / Classroom | `/api/v1/kids`, `/api/v1/classrooms` | 원생/반 관리 |
| Attendance | `/api/v1/attendance`, `/api/v1/attendance-requests/*` | 출석 처리, 승인 워크플로우, 생성 요청 `Idempotency-Key` (Swagger 설명 포함) |
| Application | `/api/v1/kid-applications/*`, `/api/v1/kindergarten-applications/*` | 입학/교사 지원 워크플로우 |
| Audit | `/api/v1/auth/audit-logs?reason=A001`, `/api/v1/domain-audit-logs?summary=입학`, export API | 운영 감사 필터/CSV export |
| Notification Ops | `/api/v1/notification-outbox?status=DEAD_LETTER&channel=EMAIL&q=smtp`, `/api/v1/notification-outbox/summary`, `/api/v1/notification-outbox/{id}/retry` | timeline/search/filter/dead-letter 재시도 |
| Dashboard | `/api/v1/dashboard/statistics` | 캐시 기반 통계 조회 |

## 테스트 & CI

혼자 운영하는 `main` 고정 포트폴리오 프로젝트라서, push마다 모든 heavy test를 돌리지 않고 빠른 실패 신호와 수동 품질 검증을 분리했습니다.

| 구분 | 실행 시점 | 하는 일 | 이유 |
|------|-----------|---------|------|
| 최소 로컬 검증 | 작은 문서/annotation/seed 수정 | `compileJava compileTestJava`, 관련 targeted test, `git diff --check` | 빠르게 깨진 import, annotation, query method를 잡음 |
| Backend CI | 모든 push | `fastTest`, `bootJar`, compose config 해석 | `main`의 빠른 실패 신호 유지 |
| Backend Quality | 큰 기능/보안/DB/성능 변경 후 수동 | `integrationTest`, `performanceSmokeTest`, `bootJar`, monitoring compose config | Testcontainers/성능 smoke는 필요할 때 비용을 지불 |
| Release check | 릴리스/면접 시연 직전 | `./gradlew test`, demo runbook 수동 확인 | 실제 시연 전 회귀 리스크 축소 |

- 통합 테스트는 H2 대체가 아니라 MySQL/Redis Testcontainers를 사용합니다.
- 대표 측정 기준으로 자동 push CI는 `5m 28s`에서 `1m 14s`로 줄었고, 최근 main push도 1분대에서 통과합니다. 정확한 최신 시간은 GitHub Actions를 기준으로 확인합니다.
- CD는 클라우드 배포 secret이 준비되기 전까지 `workflow_dispatch` 수동 실행만 유지합니다.
- Swagger/OpenAPI 공개 경로와 Prometheus scrape는 local/demo에서만 회귀 확인하고, prod에서는 기본 비활성화합니다.
- 수동 quality workflow는 실패 분석을 위해 테스트 리포트를 artifact로 업로드합니다.

## 문서

| 문서 | 설명 |
|------|------|
| [`docs/README.md`](./docs/README.md) | 문서 인덱스 |
| [`PLAN.md`](./PLAN.md) | 현재/향후 구현 계획 |
| [`docs/guides/developer-guide.md`](./docs/guides/developer-guide.md) | 개발자 가이드 |
| [`docs/guides/env-contract.md`](./docs/guides/env-contract.md) | 환경 변수 계약 |
| [`docs/guides/user-guide.md`](./docs/guides/user-guide.md) | 사용자 가이드 |
| [`docs/guides/deployment-guide.md`](./docs/guides/deployment-guide.md) | 배포 가이드 |
| [`docs/guides/interview-guide.md`](./docs/guides/interview-guide.md) | 면접관 관점 포트폴리오 설명 가이드 |
| [`docs/guides/demo-scenario.md`](./docs/guides/demo-scenario.md) | demo 시연 runbook |
| [`blog/README.md`](./blog/README.md) | 구현 배경과 글 시리즈 인덱스 |

## 라이선스

MIT License
