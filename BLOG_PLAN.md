# BLOG_PLAN.md

## 작업명
- KinderP 취업용 개발 블로그 시리즈 설계 4차 (보안/운영/Java 21 기준선 싱크 포함)

## 문서 역할
- 이 문서는 `blog/` 아래 실제 글을 쓰기 전에 기준이 되는 **블로그 집필 SSOT**다.
- 애플리케이션 개발용 SSOT는 [PLAN.md](./PLAN.md)이며, 이 문서는 오직 **블로그 시리즈 설계와 집필 운영**만 다룬다.
- 목표는 단순 회고가 아니라, **Java / Spring Boot 입문자도 이 저장소를 따라가며 “이런 순서로 설계하고 구현하면 되는구나”를 이해할 수 있는 수준의 시리즈**를 만드는 것이다.

## 0-1) 현재 후속 싱크 목표 (2026-03-28)
- 코드 리뷰 결과, 블로그는 전반적으로 강하지만 `보안 기본값`, `management surface`, `신청서 권한`, `outbox 동시성`, `배포/CI 계약`이 실제 코드 기준으로 더 정교하게 설명되어야 한다.
- 최근 배치에서는 `신청서 상세 권한`, `출결 요청 중복 가드`, `대시보드 캐시 무효화`, `Java 21 기준선`, `outbox atomic claim`, `package-smoke`, `localhost bind compose 계약`까지 현재 코드와 블로그를 함께 올렸다.
- 지금 남은 작업은 코드 추가보다 active 문서/블로그/면접 자료의 표현을 현재 코드와 완전히 맞추는 것이다.
- 특히 `JUnit suite`처럼 현재 구현보다 강한 표현, 삭제된 checkpoint script를 전제하는 문구, Redis 세션 키/장애 동작 설명을 정리해야 한다.
- 따라서 다음 코드 개선 배치는 블로그 리라이트와 분리하지 않고, **한 배치 = 코드 수정 + 테스트 + 결정 로그 + 블로그 본문 수정**으로 진행한다.
- 블로그는 “좋아 보이는 설명”보다 “실제 코드와 정확히 일치하는 설명”을 우선한다.
- 현재 마감 배치는 Batch D이며, active 문서/블로그/면접 자료의 마지막 메시지 드리프트를 제거하는 데 집중한다.

### 현재 싱크 원칙
1. 보안/운영 관련 코드가 바뀌면 같은 배치에서 관련 블로그 글도 바로 수정한다.
2. 블로그에는 반드시 아래 네 가지를 함께 적는다.
   - 바꾸기 전 무엇이 위험했는가
   - 왜 이 방식으로 고쳤는가
   - 현재 구현의 한계는 무엇인가
   - 면접에서 어떻게 설명할 것인가
3. “초보자용 설명”과 “운영/면접 관점 설명”을 같은 글 안에서 분리한다.
4. README, demo runbook, developer guide, blog 본문이 서로 다른 검증 명령이나 자격증명을 말하지 않게 유지한다.

### 현재 싱크 배치
1. Batch A: safe-by-default 설정 + management surface
   - 대상 글
     - `blog/03-docker-mysql-redis-dev-environment.md`
     - `blog/04-application-yml-and-profile-strategy.md`
     - `blog/11-securityconfig-signup-login-basics.md`
     - `blog/12-jwt-cookie-auth-flow.md`
     - `blog/17-rate-limit-and-client-ip-trust-model.md`
     - `blog/20-openapi-management-plane-and-observability.md`
   - 추가할 메시지
     - “로컬 편의 설정을 운영 기본값으로 두면 왜 위험한가”
     - “fail-open에서 fail-closed로 바꾸는 기준”
     - “환경변수/시크릿을 문서로만 맡기지 말고 부팅 단계에서 강제하는 이유”

2. Batch B: 서비스/API 권한 경계 + 상태 전이/동시성
   - 대상 글
     - `blog/10-calendar-dashboard-and-application-workflows.md`
     - `blog/22-classroom-capacity-and-waitlist-workflow.md`
     - `blog/23-attendance-change-request-and-domain-audit.md`
     - `blog/24-audit-logs-as-operations-tools.md`
   - 추가할 메시지
     - “같은 유치원 소속이라고 해서 모든 상세 정보가 보여서는 안 되는 이유”
     - “중복 요청과 동시성은 서비스 코드의 if문만으로 막을 수 없는 이유”
     - “캐시 성능과 숫자 신뢰도를 함께 관리하는 법”

3. Batch B-2: Java 21 기준선 업그레이드
   - 대상 글
     - `blog/02-gradle-spring-boot-bootstrap.md`
   - 추가할 메시지
     - “현재 저장소는 Java 21 기준선으로 컴파일하고, CI도 Temurin 21로 맞춘다”
     - “로컬은 Java 21 이상 JDK에서 `--release 21`로 맞춰 빌드할 수 있다”

4. Batch C: outbox/배포/CI/운영 계약
   - 대상 글
     - `blog/14-why-testcontainers-over-h2.md`
     - `blog/15-github-actions-and-tagged-test-suites.md`
     - `blog/19-auth-audit-log-and-operations-console.md`
     - `blog/20-openapi-management-plane-and-observability.md`
     - `blog/21-notification-outbox-and-incident-channel.md`
     - `blog/26-demo-architecture-and-interview-pack.md`
   - 추가할 메시지
     - “비동기 worker는 멀티 인스턴스에서 무엇이 먼저 깨지는가”
     - “배포 문서와 CI가 실제 코드와 한 줄이라도 어긋나면 왜 신뢰가 무너지는가”
     - “포트폴리오 문서는 멋진 요약보다 운영 계약이 더 중요하다”

### 싱크 Definition of Done
- 관련 코드와 블로그 본문이 같은 배치에서 함께 수정된다.
- 관련 글에는 `현재 구현의 한계`와 `면접에서 받을 꼬리 질문`이 최신 코드 기준으로 갱신된다.
- README, `docs/guides/developer-guide.md`, `docs/portfolio/demo/*`, 관련 블로그 글의 실행/검증 명령이 서로 일치한다.
- 보안/운영 관련 기본값은 블로그에서 “현재 구현은 이렇게 안전하게 막아 둔다”는 식으로 과장하지 않는다.
- active 인터뷰/성능 문서의 Redis 세션 키와 장애 동작 설명이 현재 코드와 어긋나지 않는다.
- 삭제된 checkpoint script나 존재하지 않는 산출물을 필수 단계처럼 안내하지 않는다.
- Batch D 마감 시 active 문서와 블로그는 최소 아래 메시지를 같은 표현으로 말해야 한다.
  - Java 21 기준선
  - local/demo만 편의 기능 허용
  - seed explicit opt-in
  - compose 기본 `127.0.0.1` bind
  - `fastTest` / `integrationTest` / `package-smoke` 검증 구조
  - outbox atomic claim은 `FOR UPDATE SKIP LOCKED` 기반

## 0) 코드베이스 스냅샷
- 도메인 패키지 수: `17`
- Flyway 마이그레이션 수: `14`
- 테스트 클래스 수: `33`
- HTML 템플릿 수: `39`
- 핵심 축
  - Bootstrapping: `settings.gradle`, `build.gradle`, `docker/*`, `application*.yml`
  - Shared foundation: `global/*`, `BaseEntity`, `ApiResponse`, `ErrorCode`
  - Core domain: `member`, `kindergarten`, `classroom`, `kid`, `attendance`, `notepad`, `announcement`, `kidapplication`, `kindergartenapplication`, `calendar`, `dashboard`
  - Security/Auth: `SecurityConfig`, `JwtTokenProvider`, `JwtFilter`, `AccessPolicyService`, `AuthService`, `OAuth2*`
  - Operational backend: `authaudit`, `domainaudit`, `notification`, `global/monitoring`, `global/logging`
  - Verification: `TestcontainersSupport`, `BaseIntegrationTest`, `AuthApiIntegrationTest`, `NotificationOutboxIntegrationTest`, `ObservabilityIntegrationTest`, `AuditConsolePerformanceSmokeTest`

## 1) 최종 목표

### 1-1. 블로그가 달성해야 하는 것
- 입문자가 본문만 읽어도 아래 흐름을 따라갈 수 있어야 한다.
  - 프로젝트 주제 선정
  - Gradle / Spring Boot 시작
  - Docker / MySQL / Redis / profile 구성
  - JPA / Flyway / Security / JWT 적용
  - 도메인 모델링
  - 기능 구현
  - 보안 하드닝
  - 테스트 현실화
  - 운영 관측성 / 감사 로그 / incident 대응
  - 포트폴리오 문서화
- 취업 준비생이 본문만 읽어도 아래 질문에 답할 수 있어야 한다.
  - “이 프로젝트는 왜 이 도메인을 골랐는가?”
  - “왜 이 구조로 패키지를 나눴는가?”
  - “왜 JWT를 세션 레지스트리까지 끌고 갔는가?”
  - “왜 멀티테넌시 권한 검증을 서비스 계층으로 올렸는가?”
  - “왜 H2가 아니라 Testcontainers를 선택했는가?”
  - “운영 가능한 백엔드라고 말할 근거가 무엇인가?”

### 1-2. Definition of Done
- `blog/` 아래에 실제 집필 가능한 시리즈 설계가 있어야 한다.
- 각 글은 반드시 아래를 포함해야 한다.
  - 해결하려는 문제
  - 실제 파일 경로
  - 실제 클래스 / 메서드
  - 테스트 / 문서 근거
  - 입문자 설명
  - 취업 포인트
- 시리즈 전체를 읽으면 “처음엔 CRUD 중심 프로젝트였지만, 점점 운영형 백엔드로 고도화했다”는 서사가 명확해야 한다.

### 1-3. 재현성 강화 목표
- 현재 블로그는 **설계 이해 / 포트폴리오 설명**에는 충분하지만, `blog/`만 보고 저장소와 거의 같은 프로젝트를 다시 만드는 수준까지는 아니다.
- 다음 단계 목표는 각 글을 “설명형 글”에서 “중간 체크포인트까지 재현 가능한 구현 가이드”로 끌어올리는 것이다.

#### 재현성 강화 기준
- 각 글에는 아래 항목이 추가되어야 한다.
  1. 시작 상태
  2. 이번 글에서 바뀌는 파일
  3. 구현 체크리스트
  4. 실행 / 검증 명령
  5. 글 종료 체크포인트
  6. 자주 막히는 지점

#### 재현성 강화 DoD
- `blog/00_rebuild_guide.md` 같은 공통 규칙 문서가 있어야 한다.
- 재현형 보강용 템플릿이 있어야 한다.
- 최소 `02`~`05`는 실제로 재현형 섹션이 들어가 있어야 한다.
- 이후 글도 같은 패턴으로 확장 가능한 운영 규칙이 정리돼 있어야 한다.

## 2) 독자 정의

### 1차 독자
- Java 문법은 알지만 Spring Boot 프로젝트를 처음 구성하는 사람
- JPA, Security, Testcontainers, CI가 아직 익숙하지 않은 사람
- “무엇부터 만들어야 하는지” 감이 없는 취업 준비생

### 2차 독자
- CRUD 프로젝트는 해봤지만 권한, 세션, 테스트, 운영성까지 올리는 법이 궁금한 사람
- 단순 기능 구현보다 “포트폴리오 설명력”을 높이고 싶은 사람

### 독자가 이 시리즈를 끝까지 읽고 얻어야 하는 것
- 기능 개발 순서가 아니라 **설계 순서**를 이해한다.
- 클래스와 메서드를 “이름만 만드는” 수준이 아니라 **책임 단위로 분리하는 기준**을 배운다.
- 테스트 / 보안 / 운영을 “마지막에 붙이는 것”이 아니라 **기능과 같이 성장시키는 축**으로 이해한다.
- 블로그 자체도 포트폴리오라는 감각을 얻는다.

## 3) 블로그가 사용할 소스 맵

### 3-1. 부트스트랩 / 실행 환경
- `settings.gradle`
- `build.gradle`
- `docker/docker-compose.yml`
- `docker/docker-compose.monitoring.yml`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-demo.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/logback-spring.xml`
- `src/main/java/com/kinderp/KinderpApplication.java`
- `src/main/java/com/kinderp/global/config/DataLoader.java`

### 3-2. DB / 스키마 진화
- `src/main/resources/db/migration/V1__init_schema.sql`
- `src/main/resources/db/migration/V2__add_application_workflow.sql`
- `src/main/resources/db/migration/V3__kid_application_unique_parent_kindergarten.sql`
- `src/main/resources/db/migration/V4__create_calendar_events.sql`
- `src/main/resources/db/migration/V5__add_performance_indexes_for_dashboard_and_notepad.sql`
- `src/main/resources/db/migration/V6__add_oauth_columns_to_member.sql`
- `src/main/resources/db/migration/V7__add_announcement_unique_views.sql`
- `src/main/resources/db/migration/V8__normalize_member_social_accounts.sql`
- `src/main/resources/db/migration/V9__preserve_social_account_history.sql`
- `src/main/resources/db/migration/V10__create_auth_audit_log.sql`
- `src/main/resources/db/migration/V11__denormalize_auth_audit_log_and_add_retention_archive.sql`
- `src/main/resources/db/migration/V12__add_notification_outbox.sql`
- `src/main/resources/db/migration/V13__add_admission_workflow_attendance_requests_and_domain_audit.sql`
- `src/main/resources/db/migration/V14__guard_pending_attendance_change_requests.sql`

### 3-3. 공통 백엔드 토대
- `src/main/java/com/kinderp/global/common/*`
- `src/main/java/com/kinderp/global/exception/*`
- `src/main/java/com/kinderp/global/config/*`
- `src/main/java/com/kinderp/global/logging/*`
- `src/main/java/com/kinderp/global/monitoring/*`

### 3-4. 보안 / 인증 / 권한
- `src/main/java/com/kinderp/global/config/SecurityConfig.java`
- `src/main/java/com/kinderp/global/security/jwt/*`
- `src/main/java/com/kinderp/global/security/oauth2/*`
- `src/main/java/com/kinderp/global/security/access/AccessPolicyService.java`
- `src/main/java/com/kinderp/global/security/ClientIpResolver.java`
- `src/main/java/com/kinderp/global/security/AuthenticatedMemberResolver.java`
- `src/main/java/com/kinderp/domain/auth/*`
- `src/main/java/com/kinderp/domain/member/entity/MemberSocialAccount.java`

### 3-5. 핵심 도메인
- `src/main/java/com/kinderp/domain/member/*`
- `src/main/java/com/kinderp/domain/kindergarten/*`
- `src/main/java/com/kinderp/domain/classroom/*`
- `src/main/java/com/kinderp/domain/kid/*`
- `src/main/java/com/kinderp/domain/attendance/*`
- `src/main/java/com/kinderp/domain/notepad/*`
- `src/main/java/com/kinderp/domain/announcement/*`
- `src/main/java/com/kinderp/domain/kidapplication/*`
- `src/main/java/com/kinderp/domain/kindergartenapplication/*`
- `src/main/java/com/kinderp/domain/calendar/*`
- `src/main/java/com/kinderp/domain/dashboard/*`
- `src/main/java/com/kinderp/domain/notification/*`
- `src/main/java/com/kinderp/domain/authaudit/*`
- `src/main/java/com/kinderp/domain/domainaudit/*`

### 3-6. 테스트 / CI / 운영 문서
- `src/test/java/com/kinderp/common/TestcontainersSupport.java`
- `src/test/java/com/kinderp/common/BaseIntegrationTest.java`
- `src/test/java/com/kinderp/api/*`
- `src/test/java/com/kinderp/integration/*`
- `src/test/java/com/kinderp/performance/*`
- `.github/workflows/ci.yml`
- `docs/decisions/phase00_setup.md` ~ `docs/decisions/phase44_tagged_ci_readiness_and_hiring_pack.md`
- `docs/portfolio/architecture/system-architecture.md`
- `docs/portfolio/demo/demo-preflight.md`
- `docs/portfolio/demo/demo-runbook.md`
- `docs/portfolio/case-studies/auth-incident-response.md`
- `docs/portfolio/hiring-pack/backend-hiring-pack.md`

## 4) 서사 전략

### 4-1. 실제 개발 순서와 게시 순서를 분리한다
- 이 프로젝트는 실제로 `기능 구현 -> 보안 문제 발견 -> 테스트 현실화 -> 운영형 기능 확장 -> 문서 패키징` 순서로 자랐다.
- 하지만 입문자에게 그대로 보여주면 맥락 전환이 너무 잦다.
- 그래서 시리즈는 아래 두 축을 동시에 만족하도록 재구성한다.
  - **학습 순서**: 입문자가 따라가기 쉬운 순서
  - **실전 서사**: 나중에 왜 리팩터링 / 하드닝이 필요했는지 설명 가능한 순서

### 4-2. 기본 원칙
- “처음 버전”을 먼저 설명하고, “왜 문제가 생겼는지”를 보여준 뒤, “개선 버전”으로 넘어간다.
- 코드만 설명하지 않고 반드시 아래 세 층을 함께 설명한다.
  - 도메인 규칙
  - 보안 / 운영 규칙
  - 테스트 / 문서 근거
- 글의 중심은 항상 한 문장으로 요약 가능해야 한다.
  - 예: `JWT를 붙였다`가 아니라 `JWT를 세션 운영 가능한 구조로 키웠다`

### 4-3. 글 하나당 품질 기준
- 하나의 핵심 질문만 다룬다.
- 실제 클래스 / 메서드 3~8개를 중심으로 설명한다.
- SQL migration, 테스트, 문서를 최소 하나 이상 근거로 붙인다.
- “입문자 설명”과 “취업 포인트”를 분리한다.
- 긴 코드 전체를 붙이지 않고, 흐름과 책임을 설명한다.

## 5) 실제 코드 기준 개발 연대기

### Stage A. 기초 구축
- `settings.gradle`, `build.gradle`, `KinderpApplication`
- `application.yml`, `application-local.yml`
- `V1__init_schema.sql`
- 초기 도메인: `Member`, `Kindergarten`, `Classroom`, `Kid`

### Stage B. 기능 확장
- `Attendance`, `Notepad`, `Announcement`
- `KidApplication`, `KindergartenApplication`
- `Calendar`, `Dashboard`
- `V2`, `V3`, `V4`, `V5`

### Stage C. 보안 / 인증 고도화
- `AccessPolicyService`
- `AuthService`, `JwtTokenProvider`, `AuthSessionRegistryService`
- `AuthRateLimitService`, `ClientIpResolver`
- `OAuth2AuthenticationSuccessHandler`, `SocialAccountLinkService`
- `V6`, `V7`, `V8`, `V9`

### Stage D. 운영형 백엔드 고도화
- `AuthAuditLogService`, `AuthAuditRetentionService`
- `NotificationDispatchService`
- `DomainAuditLogService`
- `CriticalDependenciesHealthIndicator`
- `PrometheusScrapeController`, `RequestLoggingFilter`, `CorrelationIdFilter`
- `V10`, `V11`, `V12`, `V13`

### Stage E. 포트폴리오 패키징
- OpenAPI / Swagger
- Demo seed / demo profile
- Grafana / Prometheus
- Hiring pack / architecture / demo runbook / interview docs

## 6) 게시 순서

이 섹션은 **현재 실제 공개 글 순서**를 반영한 SSOT입니다.
세부 글 제목과 Part 구분은 [blog/README.md](./blog/README.md)와 동일하게 유지합니다.

### Part A. 문제 정의
- `01. 왜 유치원 ERP를 주제로 잡았는가`
  - 역할, 상태 전이, 테넌트 경계가 자연스럽게 나오는 도메인 선택 이유

### Part B. 부트스트랩과 공통 설정
- `02. Gradle과 Spring Boot 뼈대 만들기`
  - `settings.gradle`, `build.gradle`, `KinderpApplication`
- `03. Docker로 MySQL / Redis 개발 환경 만들기`
  - `docker-compose.yml`, monitoring overlay
- `04. application.yml과 profile 전략 설계하기`
  - `application*.yml`, `logback-spring.xml`, `DataLoader`
- `05. JPA / Flyway / QueryDSL / Redis / Cache 공통 기반 잡기`
  - `JpaConfig`, `QuerydslConfig`, `RedisConfig`, `CacheConfig`, migration 전략
- `06. global / domain 패키지 구조를 어떻게 잡았는가`
  - `ApiResponse`, `BaseEntity`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`

### Part C. 핵심 도메인 만들기
- `07. Member, Kindergarten, Classroom으로 첫 관계 만들기`
  - 회원/유치원/반 모델링과 기본 관계
- `08. Kid, ParentKid, Attendance로 첫 업무 Aggregate 만들기`
  - 원생, 보호자 관계, 출결 aggregate
- `09. 알림장, 공지, 알림으로 기능 확장하기`
  - 읽기/쓰기 기능 확장과 알림 흐름
- `10. 일정, 대시보드, 신청 도메인으로 조회와 상태 전이를 넓히기`
  - `Calendar`, `Dashboard`, `KidApplication`, `KindergartenApplication`의 기본 뼈대

### Part D. 인증과 보안
- `11. SecurityConfig와 회원가입/로그인 기본 흐름 만들기`
- `12. JwtTokenProvider, JwtFilter, AuthService로 쿠키 JWT 연결하기`
- `13. 멀티테넌시 보안 문제를 어떻게 발견하고 고쳤는가`

### Part E. 테스트와 CI
- `14. 왜 H2가 아니라 Testcontainers였는가`
- `15. GitHub Actions와 태그 기반 테스트 태스크/CI 분리`

### Part F. 인증을 운영형으로 발전시키기
- `16. Refresh rotation과 활성 세션 제어`
- `17. Rate limit과 Client IP 신뢰 모델`
- `18. OAuth2와 소셜 계정 lifecycle을 안전하게 설계하기`

### Part G. 운영성, 감사 추적, 장애 대응
- `19. 인증 감사 로그와 원장용 운영 콘솔 만들기`
- `20. OpenAPI, 관리면, 관측성을 한 번에 설계하기`
- `21. 알림 전달을 Outbox, Retry, Dead-letter로 바꾸기`

### Part H. 운영형 워크플로우
- `22. 반 정원, 대기열, 입학 제안 워크플로우 설계하기`
- `23. 출결 변경 요청과 업무 감사 로그를 함께 설계하기`
- `24. 감사 로그를 운영 도구로 키우기`

### Part I. 성능과 포트폴리오 마감
- `25. 성능 개선을 코드가 아니라 스토리로 설명하는 방법`
- `26. 데모, 아키텍처, 면접 패키지까지 묶어 프로젝트를 완성하기`

## 7) 글쓰기 작업 규칙

### 7-1. 각 글의 기본 구조
- 문제
- 선행 개념
- 다룰 파일
- 설계 선택
- 클래스 / 메서드 설명
- 요청 흐름 / 데이터 흐름
- 테스트 근거
- 회고
- 취업 포인트

### 7-2. 코드 설명 방식
- 소스 전체를 붙이지 않는다.
- 긴 메서드는 아래 방식으로 설명한다.
  - 입력
  - 출력
  - 핵심 분기
  - 책임
  - 왜 이 메서드가 필요한지
- 반드시 “메서드가 하나의 책임을 갖는 이유”를 함께 설명한다.

### 7-3. 근거 문서 사용 방식
- `docs/decisions/*`는 “왜 이 변경이 필요했는지” 설명할 때 사용한다.
- `docs/portfolio/*`는 “현재 기준에서 어떻게 보여줄 것인지” 설명할 때 사용한다.
- 블로그는 기존 문서를 복붙하지 않고, 학습 흐름에 맞게 재구성한다.

### 7-4. 테스트 설명 방식
- 성공 케이스만 적지 않는다.
- 반드시 실패 / 차단 / 경계 조건을 함께 설명한다.
- 예시
  - 다른 유치원 접근 차단
  - 만료된 offer 수락 차단
  - 소셜 provider 교체 금지
  - rate limit 초과
  - readiness DOWN / liveness UP

## 8) 유지보수 / 리라이트 우선순위

이제 시리즈 전체 초안은 완성됐으므로, 우선순위는 “처음 쓰기”가 아니라 “더 읽기 좋고 더 재현 가능하게 다듬기” 기준으로 잡습니다.

### 1차 우선 리라이트
- `README`, `01`, `10`, `26`
- 이유
  - 독자가 처음 만나는 진입점과 마감 글의 완성도가 전체 인상을 좌우한다
  - 연대기 혼선이나 과도한 후반부 스포일러를 먼저 줄여야 한다

### 2차 우선 리라이트
- `06`, `22`, `23`, `24`
- 이유
  - 공통 규약, 운영형 워크플로우, 감사 로그는 재현성 설명이 가장 자주 흔들리는 구간이다
  - 단계 체크포인트 스크립트와 산출물 체크리스트가 특히 효과적이다

### 3차 우선 리라이트
- 외부 플랫폼용 permalink 변환
- 이유
  - 현재 `blog/`와 `docs/portfolio/*`는 repo-relative 링크로 정리됐다
  - Velog/Notion 공개 전에는 GitHub permalink 기반으로 한 번 더 바꿔야 한다

## 9) 집필 운영 방식

### 9-1. 글 하나를 쓸 때 먼저 확인할 것
- 관련 코드 파일
- 관련 SQL migration
- 관련 테스트
- 관련 결정 로그
- 관련 시연 화면 / 템플릿

### 9-2. 글 하나를 쓸 때 남길 산출물
- 본문 초안
- 사용할 다이어그램 목록
- 설명할 메서드 목록
- 테스트 근거 목록
- 면접 질문 예상 2~3개

### 9-3. 글을 쓴 뒤 체크할 것
- 입문자도 따라올 수 있는가
- 실제 파일과 메서드가 들어갔는가
- “왜 이렇게 설계했는가”가 드러나는가
- 취업 포인트가 억지스럽지 않고 코드와 연결되는가

## 10) 리스크와 대응
- 리스크: 글이 너무 방대해져서 한 편이 책처럼 길어질 수 있다
  - 대응: 글마다 하나의 질문만 다룬다
- 리스크: 기존 결정 로그와 중복될 수 있다
  - 대응: 결정 로그는 사실 근거, 블로그는 학습형 해설로 역할을 나눈다
- 리스크: 초심자 설명과 취업 포인트가 섞여 산만해질 수 있다
  - 대응: 본문 안에 `입문자 설명`, `취업 포인트`를 명시적으로 분리한다
- 리스크: 후반부 운영 문서가 너무 고급처럼 보일 수 있다
  - 대응: 먼저 문제를 쉬운 말로 설명하고, 그 다음 용어를 붙인다

## 11) 다음 액션
- `blog/README.md`를 독자용/작성자용 동선 기준으로 계속 유지
- 재현성이 약한 글은 먼저 `산출물 체크리스트 + 안정 검증 명령`으로 보강하고, 꼭 필요할 때만 checkpoint script를 추가
- 외부 플랫폼 배포 직전에는 repo-relative 링크를 GitHub permalink로 변환
- 각 리라이트 배치의 근거와 검증 결과를 `BLOG_PROGRESS.md`에 계속 누적

## 12) 재현성 강화 실행 배치

### Batch R1. 공통 재현형 규칙 문서
- `blog/00_rebuild_guide.md`
- `blog/00_quality_checklist.md`
- `blog/README.md`에 재현형 읽기 순서 연결

### Batch R2. 부트스트랩 구간 우선 보강
- 대상: `02`, `03`, `04`, `05`
- 이유
  - 환경 / 설정 / 실행이 안 잡히면 뒤 글은 재현이 불가능하다
  - 입문자가 가장 빨리 막히는 구간이다

### Batch R3. 인증 / 테스트 구간 보강
- 대상: `11`~`15`
- 이유
  - Security, JWT, Testcontainers, CI는 설명형 글만으로 따라가기가 가장 어렵다

### Batch R4. 운영형 기능 / 포트폴리오 구간 보강
- 대상: `16`~`26`
- 이유
  - 상태 전이, 감사 로그, 관측성은 체크포인트와 실패 대응까지 같이 있어야 재현성이 생긴다

### Batch R5. 단계 체크포인트 스크립트 도입
- 대상: 설명형 검증이 약했던 핵심 글
  - `06`, `10`, `22`, `23`, `24`, `26`
- 이유
  - 최종 저장소 기준 통합 스위트만으로는 “이 단계까지 왔다”를 확인하기 어렵다
  - 파일/메서드/문서 산출물을 점검하는 경량 스크립트가 필요하다

### Batch R6. 공개 배포용 링크 전략 정리
- 대상: `blog/README.md`, 공개 전 변환 체크리스트
- 이유
  - repo-relative 링크는 GitHub에서는 잘 동작하지만 외부 플랫폼에서는 그대로 유지되지 않는다
  - 집필 원본과 배포용 permalink 변환본의 경계를 문서화해야 한다
