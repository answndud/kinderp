# KinderP 개발 블로그 시리즈 요약 인덱스

이 문서는 공개용 목차다.

상세 집필 전략, 실제 코드 근거, 메서드 단위 설명 계획, 테스트/결정 로그 매핑은 [BLOG_PLAN.md](../BLOG_PLAN.md)를 SSOT로 사용한다.

## 공개 순서

### Part A. 문제 정의

1. [왜 유치원 ERP를 주제로 잡았는가](./01-why-kinderp-domain.md)

### Part B. 부트스트랩과 공통 설정

2. [`settings.gradle`, `build.gradle`, `KinderpApplication`으로 시작하기](./02-gradle-spring-boot-bootstrap.md)
3. [Docker로 MySQL / Redis / monitoring overlay 만들기](./03-docker-mysql-redis-dev-environment.md)
4. [`application.yml`과 profile 전략 설계하기](./04-application-yml-and-profile-strategy.md)
5. [JPA / Flyway / QueryDSL / Redis / Cache 공통 설정 잡기](./05-jpa-flyway-querydsl-redis-cache-foundation.md)
6. [`global` / `domain` 패키지 구조를 어떻게 잡았는가](./06-global-and-domain-package-structure.md)

### Part C. 핵심 도메인 만들기

7. [`Member`, `Kindergarten`, `Classroom`으로 첫 관계 만들기](./07-member-kindergarten-classroom-modeling.md)
8. [`Kid`, `ParentKid`, `Attendance`로 첫 업무 Aggregate 만들기](./08-kid-parentkid-attendance-aggregate.md)
9. [알림장, 공지, 알림으로 기능을 확장하는 법](./09-notepad-announcement-notification-expansion.md)
10. [일정, 대시보드, 신청 도메인으로 조회와 상태 전이를 넓히기](./10-calendar-dashboard-and-application-workflows.md)

### Part D. 인증과 보안

11. [`SecurityConfig`와 회원가입/로그인 기본 흐름 만들기](./11-securityconfig-signup-login-basics.md)
12. [`JwtTokenProvider`, `JwtFilter`, `AuthService`로 쿠키 JWT 연결하기](./12-jwt-cookie-auth-flow.md)
13. [멀티테넌시 보안 문제를 어떻게 발견하고 고쳤는가](./13-multitenant-access-hardening.md)

### Part E. 테스트와 CI

14. [H2가 아니라 Testcontainers를 붙인 이유](./14-why-testcontainers-over-h2.md)
15. [GitHub Actions와 태그 기반 테스트 태스크/CI 분리](./15-github-actions-and-tagged-test-suites.md)

### Part F. 인증을 운영형으로 발전시키기

16. [Refresh rotation과 활성 세션 제어로 인증을 한 단계 올리기](./16-refresh-rotation-and-active-session-control.md)
17. [로그인 남용 방어: Rate Limit과 Client IP 신뢰 모델](./17-rate-limit-and-client-ip-trust-model.md)
18. [OAuth2와 소셜 계정 lifecycle을 안전하게 설계하기](./18-oauth2-social-account-lifecycle.md)

### Part G. 운영성, 감사 추적, 장애 대응

19. [인증 감사 로그를 어떻게 설계하고 운영 콘솔까지 연결했는가](./19-auth-audit-log-and-operations-console.md)
20. [OpenAPI, management plane, Actuator, Prometheus, structured logging](./20-openapi-management-plane-and-observability.md)
21. [알림 전달을 `notification_outbox`로 비동기화한 이유](./21-notification-outbox-and-incident-channel.md)

### Part H. 운영형 워크플로우

22. [반 정원, waitlist, offer expiry로 입학 워크플로우를 모델링하기](./22-classroom-capacity-and-waitlist-workflow.md)
23. [학부모 출결 변경 요청과 업무 감사 로그를 왜 같이 설계했는가](./23-attendance-change-request-and-domain-audit.md)

### Part I. 시연과 취업 패키징

24. [인증 감사 로그와 업무 감사 로그를 운영 도구로 키우기](./24-audit-logs-as-operations-tools.md)
25. [성능 개선을 측정 가능한 포트폴리오 스토리로 바꾸기](./25-performance-story-as-portfolio.md)
26. [Demo seed, 아키텍처 문서, 면접 자료로 프로젝트를 마감하기](./26-demo-architecture-and-interview-pack.md)

## 실제 집필 우선순위

공개는 1번부터 하지만, 실제 집필은 아래 순서로 먼저 진행한다.

1. 02. Gradle / Spring Boot bootstrap
2. 03. Docker / MySQL / Redis
3. 04. application.yml / profile
4. 11. SecurityConfig / 로그인
5. 13. 멀티테넌시 권한 하드닝

이유는 코드 근거가 명확하고, 입문자 유입과 취업 포인트가 가장 빠르게 드러나기 때문이다.
