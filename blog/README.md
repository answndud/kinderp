# Blog Workspace

이 폴더는 Kindergarten ERP를 기반으로 작성할 **취업용 개발 블로그 시리즈**의 작업 공간입니다.

목표는 두 가지입니다.

1. Java / Spring Boot 입문자가 “이런 순서로 프로젝트를 설계하고 개발하면 되는구나”를 이해하게 만들기
2. 취업 준비생이 “기능 구현 -> 보안 -> 테스트 -> 운영 -> 포트폴리오 패키징”으로 성장하는 흐름을 따라가게 만들기

그리고 이제 세 번째 목표가 추가됐습니다.

3. 독자가 `blog/`만 보고도 각 단계의 **중간 체크포인트를 재현**할 수 있게 만들기

## 블로그 작업 SSOT

- [BLOG_PLAN.md](../BLOG_PLAN.md)
  - 블로그 시리즈 전체 계획
  - 집필 순서와 범위
- [BLOG_PROGRESS.md](../BLOG_PROGRESS.md)
  - 블로그 작업 진행 로그
- [blog/00_rebuild_guide.md](./00_rebuild_guide.md)
  - 블로그를 재현형 구현 가이드로 강화하는 공통 규칙
- [blog/00_quality_checklist.md](./00_quality_checklist.md)
  - 초보자 이해도 / 재현성 / 면접 대응력 기준의 편집 체크리스트

루트 [PLAN.md](../PLAN.md)는 **애플리케이션 개발 작업용 SSOT**로 유지합니다.

## 링크 정책

- `blog/`와 이 시리즈가 직접 참조하는 저장소 문서는 **repo-relative 링크**로 정리돼 있습니다.
- 따라서 GitHub 저장소 안에서는 바로 읽을 수 있습니다.
- 다만 Velog, Notion 같은 외부 플랫폼으로 옮길 때는 GitHub permalink처럼 절대 URL 체계로 한 번 더 변환해야 합니다.

## 이 폴더의 역할

- `00_series_plan.md`
  - 연재 전체 구조
  - 글 순서
  - 글마다 다룰 파일/클래스/설정 범위
  - 독자 학습 목표
- `00_rebuild_guide.md`
  - 재현형 글을 쓸 때 지켜야 할 공통 규칙
- `00_quality_checklist.md`
  - 각 글을 실제로 수정할 때 적용할 품질 점검표

## 이 시리즈가 다루는 범위

- 프로젝트 주제 선정과 요구사항 정리
- Gradle / Spring Boot 프로젝트 초기 구성
- Docker, MySQL, Redis, profile 설계
- JPA / Flyway / Redis / Security 기초 설정
- 도메인 모델링
- 인증/JWT/OAuth2
- 출석/알림장/공지/신청/승인
- 멀티테넌시 권한 하드닝
- Testcontainers / GitHub Actions / 테스트 전략
- 감사 로그 / Outbox / Observability / Incident 대응
- 운영형 워크플로우
- 면접/데모/문서 패키징

## 집필 원칙

- “무엇을 만들었는가”보다 “왜 이렇게 설계했는가”를 먼저 설명한다.
- 매 글마다 실제 코드 파일과 클래스 이름을 명시한다.
- 가능하면 메서드 단위까지 내려간다.
- 글 구조는 항상 `문제 -> 설계 -> 코드 -> 테스트 -> 회고` 순서를 유지한다.
- 초심자를 위해 용어를 바로 쓰지 않고 한 번은 풀어서 설명한다.
- 각 글 끝에는 반드시 “취업 포인트”를 넣는다.

## 연재 인덱스

### Part A. 문제 정의

1. [왜 유치원 ERP를 주제로 잡았는가](./01-why-kindergarten-erp-domain.md)

### Part B. 부트스트랩과 공통 설정

2. [Gradle과 Spring Boot 뼈대 만들기](./02-gradle-spring-boot-bootstrap.md)
3. [Docker로 MySQL / Redis 개발 환경 만들기](./03-docker-mysql-redis-dev-environment.md)
4. [application.yml과 profile 전략 설계하기](./04-application-yml-and-profile-strategy.md)
5. [JPA / Flyway / QueryDSL / Redis / Cache 공통 기반 잡기](./05-jpa-flyway-querydsl-redis-cache-foundation.md)
6. [global / domain 패키지 구조를 어떻게 잡았는가](./06-global-and-domain-package-structure.md)

### Part C. 핵심 도메인 만들기

7. [Member, Kindergarten, Classroom으로 첫 관계 만들기](./07-member-kindergarten-classroom-modeling.md)
8. [Kid, ParentKid, Attendance로 첫 업무 Aggregate 만들기](./08-kid-parentkid-attendance-aggregate.md)
9. [알림장, 공지, 알림으로 기능 확장하기](./09-notepad-announcement-notification-expansion.md)
10. [일정, 대시보드, 신청 도메인으로 조회와 상태 전이를 넓히기](./10-calendar-dashboard-and-application-workflows.md)

### Part D. 인증과 보안

11. [SecurityConfig와 회원가입/로그인 기본 흐름 만들기](./11-securityconfig-signup-login-basics.md)
12. [JwtTokenProvider, JwtFilter, AuthService로 쿠키 JWT 연결하기](./12-jwt-cookie-auth-flow.md)
13. [멀티테넌시 보안 문제를 어떻게 발견하고 고쳤는가](./13-multitenant-access-hardening.md)

### Part E. 테스트와 CI

14. [왜 H2가 아니라 Testcontainers였는가](./14-why-testcontainers-over-h2.md)
15. [GitHub Actions와 태그 기반 테스트 태스크/CI 분리](./15-github-actions-and-tagged-test-suites.md)

### Part F. 인증을 운영형으로 발전시키기

16. [Refresh rotation과 활성 세션 제어](./16-refresh-rotation-and-active-session-control.md)
17. [Rate limit과 Client IP 신뢰 모델](./17-rate-limit-and-client-ip-trust-model.md)
18. [OAuth2와 소셜 계정 lifecycle을 안전하게 설계하기](./18-oauth2-social-account-lifecycle.md)

### Part G. 운영성, 감사 추적, 장애 대응

19. [인증 감사 로그와 원장용 운영 콘솔 만들기](./19-auth-audit-log-and-operations-console.md)
20. [OpenAPI, 관리면, 관측성을 한 번에 설계하기](./20-openapi-management-plane-and-observability.md)
21. [알림 전달을 Outbox, Retry, Dead-letter로 바꾸기](./21-notification-outbox-and-incident-channel.md)

### Part H. 운영형 워크플로우

22. [반 정원, 대기열, 입학 제안 워크플로우 설계하기](./22-classroom-capacity-and-waitlist-workflow.md)
23. [출결 변경 요청과 업무 감사 로그를 함께 설계하기](./23-attendance-change-request-and-domain-audit.md)
24. [감사 로그를 운영 도구로 키우기](./24-audit-logs-as-operations-tools.md)

### Part I. 성능과 포트폴리오 마감

25. [성능 개선을 코드가 아니라 스토리로 설명하는 방법](./25-performance-story-as-portfolio.md)
26. [데모, 아키텍처, 면접 패키지까지 묶어 프로젝트를 완성하기](./26-demo-architecture-and-interview-pack.md)

## 독자용 시작 순서

1. `00_rebuild_guide.md`로 이 시리즈를 어떻게 읽고 재현할지 먼저 확인합니다.
2. `00_quality_checklist.md`로 좋은 구현 글이 무엇을 만족해야 하는지 먼저 봅니다.
3. `00_series_plan.md`로 전체 지도를 한 번 훑습니다.
4. 실제 읽기는 위 인덱스를 따라 `01`번부터 `26`번까지 순서대로 진행합니다.
5. 구현 글에서는 본문보다 먼저 글 하단의 `시작 상태`, `바뀌는 파일`, `산출물 체크리스트`, `실행 / 검증 명령`을 같이 봅니다.

## 작성자용 시작 순서

1. `BLOG_PLAN.md`로 현재 연재 구조와 리라이트 우선순위를 확인합니다.
2. `BLOG_PROGRESS.md`로 최근 수정 이력과 남은 작업을 확인합니다.
3. `00_rebuild_guide.md`와 `00_quality_checklist.md`를 기준으로 글 구조와 품질 기준을 맞춥니다.
4. 단계별 체크포인트는 산출물 체크리스트와 안정 검증 명령을 기준으로 관리하고, 별도 스크립트는 실제 파일이 있을 때만 문서에 노출합니다.
