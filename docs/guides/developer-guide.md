# KinderP 개발자 가이드

이 문서는 이 저장소에서 기능을 추가/수정하는 개발자를 위한 실무 가이드입니다.
유저 관점 설명은 `docs/guides/user-guide.md`를 참고하세요.
실행 환경별 필수 변수와 기본 노출 정책은 `docs/guides/env-contract.md`를 SSOT로 봅니다.
현재/향후 구현 작업은 저장소 루트 `PLAN.md`를 기준으로 관리합니다.

---

## 1. 프로젝트 개요

- 프로젝트: KinderP
- 핵심 역할: `PRINCIPAL`, `TEACHER`, `PARENT`
- 아키텍처: `domain/{controller,service,repository,entity,dto}` + `global/*`
- 기술 스택:
  - Java 21
  - Spring Boot 3.5.14
  - Spring Data JPA + QueryDSL
  - Spring Security + JWT(HTTP-only cookie)
  - MySQL 8, Redis
  - Thymeleaf + HTMX + Alpine.js + Tailwind(로컬 빌드)
  - Flyway

핵심 원칙은 **Simple is Best** 입니다.

업무 기준 날짜와 자동 기록 시간은 `ProductTime`을 통해 `Asia/Seoul` 기준으로 계산합니다. 서버 운영체제의 기본 시간대에 의존하지 않습니다. 도메인 코드의 직접적인 `LocalDate.now()`·`LocalDateTime.now()`·`LocalTime.now()` 호출은 CI의 `npm run security:product-time`으로 차단합니다.

---

## 2. 로컬 실행

## 인프라

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
docker compose --env-file docker/.env -f docker/docker-compose.yml down
```

## 빌드/실행

```bash
./gradlew clean build
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew bootRun --args='--spring.profiles.active=demo'
```

- 기본값은 fail-closed입니다. `SPRING_PROFILES_ACTIVE`를 명시하지 않으면 부팅을 허용하지 않습니다.
- local 시드가 필요하면 `APP_SEED_ENABLED=true`를 함께 넘기세요.
- 로컬 `JAVA_HOME`이 깨져 있다면 전역 shell 설정을 바로 바꾸지 말고 아래처럼 명령 단위로 containment합니다.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew fastTest
```

## 테스트

```bash
./gradlew fastTest
./gradlew integrationTest
./gradlew performanceSmokeTest
./gradlew bootJar
./gradlew test
```

- 로컬 compose 포트는 기본적으로 `127.0.0.1`에만 바인딩됩니다.
- 배포 단위 확인이 필요하면 `bootJar`를 만들고 compose config를 함께 확인하세요.
- `Backend CI`는 push마다 `fastTest`, `bootJar`, compose config만 자동 실행합니다.
- `Backend Quality`는 수동 workflow이며 `integrationTest`, `performanceSmokeTest`, `bootJar`, compose config를 한 번에 확인합니다.
- 기능/보안/DB/운영 설정을 바꾼 뒤에는 로컬에서 관련 targeted test를 먼저 실행하고, push 후 필요하면 수동 `Backend Quality`를 실행하세요.

---

## 3. 디렉토리 구조와 책임

## 백엔드

- `src/main/java/com/erp/domain/*`
  - `controller`: API/뷰 진입점
  - `service`: 비즈니스 로직, 트랜잭션 경계
  - `repository`: 데이터 접근
  - `entity`: JPA 엔티티
  - `dto`: 요청/응답 객체
- `src/main/java/com/erp/global/*`
  - `config`: JPA, QueryDSL, Security, MVC Interceptor, Cache
  - `security`: JWT 필터/프로바이더/UserDetails
  - `exception`: `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`
  - `common`: `ApiResponse`, `BaseEntity`

## 프론트

- `src/main/resources/templates/*`: 역할 기반 SSR 화면
- `src/main/resources/static/js/app.js`: 공통 UI/SweetAlert2/알림 유틸
- `src/main/resources/static/js/notifications.js`: 알림 API·자동 갱신 런타임
- `src/main/resources/static/js/header.js`: header 메뉴·알림 패널 controller
- `src/main/resources/static/js/pages/*`: 화면별 업무 모듈(`dashboard.js`, `kids.js`, `attendance.js`, `applications.js` 등)
- Tailwind는 템플릿을 content로 읽는 로컬 CLI 빌드(`npm run frontend:build`)로 정적 자산을 생성합니다.
- HTMX로 fragment 단위 갱신, Alpine.js로 최소 상태 관리
- 디자인/프론트엔드 개선 컨텍스트는 루트 `.impeccable.md`를 기준으로 합니다.
- Impeccable skill은 repo-local `.agents/skills/*`에만 둡니다. 전역 `~/.codex`, `~/.claude`, global npm install, 사용자 홈 설정은 변경하지 않습니다.
- detector는 전역 설치 없이 npm script로 실행하며, 기본 대상은 템플릿/JS 마크업입니다. 생성 CSS는 `npm run css:build`와 브라우저에서 별도로 검증합니다.

```bash
npm run impeccable:detect
npm run impeccable:detect -- --fast
npm run impeccable:detect:json -- --fast
npm run accessibility:templates
npm run security:inline-handlers
npm run security:view-errors
npm run e2e:smoke
```

- 이 detector script는 `impeccable@2.1.7`을 `npm exec --package`로 실행하고, repo-local `.npmrc`와 스크립트 환경변수로 npm cache/log를 저장소 내부 `.cache/npm`에 둡니다.
- UI 개선 작업 흐름은 audit/critique로 문제 정리, 구현, polish, detector 및 Gradle 검증 순서를 권장합니다.
- `accessibility:templates`는 비숨김 폼 컨트롤의 label/ARIA 이름, 버튼 `type`, 이미지 `alt` 누락을 빠르게 검사하며 push CI에서도 실행합니다.
- `security:view-errors`는 SSR 뷰 컨트롤러가 내부 예외 메시지를 사용자 flash message로 노출하지 않는지 검사합니다. 상세 원인은 서버 로그에만 남깁니다.

## DB

- `src/main/resources/db/migration/V*.sql`: Flyway 마이그레이션

---

## 4. 인증/인가/리다이렉트 동작

## 인증

- API 로그인: `POST /api/v1/auth/login`
- JWT access/refresh 토큰은 쿠키 기반
- refresh token은 Redis TTL 저장

## 인가

- `@PreAuthorize` + `SecurityConfig` URL 규칙 병행
- 역할 enum은 반드시 `PRINCIPAL`, `TEACHER`, `PARENT`만 사용
- Swagger/OpenAPI, app-port Prometheus, demo seed는 기본 공개가 아니라 명시적 opt-in입니다.

## 상태 기반 강제 리다이렉트

`RoleRedirectInterceptor`가 다음을 강제합니다.

- 원장 + 유치원 미등록: `/kindergarten/create`
- 선생/학부모 + `PENDING` 또는 유치원 미배정: `/applications/pending`

신규 화면 추가 시 이 인터셉터 영향 범위를 먼저 점검하세요.

---

## 5. 도메인별 핵심 기능 맵

- `auth/member`: 가입/로그인/프로필/비밀번호/탈퇴
- `kindergarten/classroom`: 유치원/반 생성 및 운영
- `kid`: 원생 관리, 부모 연결
- `attendance`: 일별 출결, 특수 출결 상태, 월간 리포트
- `notepad`: 반/원생 알림장, 읽음 처리
- `announcement`: 공지 및 중요 공지
- `kindergartenapplication/kidapplication`: 지원/승인
- `notification`: 배지/목록/읽음/삭제
- `notification-outbox`: 원장 전용 timeline/status/channel/search/dead-letter retry 운영 API
- `calendar`: 유치원/반/개인 일정
- `dashboard`: 통계(원장)

---

## 6. API/코드 스타일 규칙

## API

- prefix: `/api/v1/**`
- 응답: `ApiResponse<T>`
- 기존 계약은 수정보다 확장 우선

## DTO

- 요청: `*Request`
- 응답: `*Response` (가능하면 record 고려)
- 컨트롤러 입력 경계에 `@Valid`, `@NotNull`, `@NotBlank`

## 예외 처리

- 서비스에서 비즈니스 오류는 `BusinessException(ErrorCode)`
- 에러 포맷은 `ApiResponse.error` 계약 유지

## 트랜잭션/JPA

- 서비스 클래스 기본 `@Transactional(readOnly = true)` 권장
- 쓰기 메서드만 `@Transactional`
- OSIV OFF 전제 (`open-in-view=false`)
- Controller/View에서 lazy 초기화 의존 금지

---

## 7. DB 마이그레이션 규칙

- 위치: `src/main/resources/db/migration/`
- 파일명: `V{version}__{description}.sql`
- 운영 파괴 작업 금지
- 인덱스는 실제 조회 패턴 기준으로 추가
- soft delete 엔티티는 `deletedAt` 필터 일관성 유지

현재 주요 마이그레이션:

- `V1__init_schema.sql`: 초기 핵심 테이블
- `V2__add_application_workflow.sql`: 지원/승인/알림
- `V3__kid_application_unique_parent_kindergarten.sql`: 중복 신청 제약
- `V4__create_calendar_events.sql`: 캘린더
- `V5__add_performance_indexes_for_dashboard_and_notepad.sql`: 성능 인덱스
- `V15__drop_notepad_legacy_is_read.sql`: `notepad.is_read` 레거시 컬럼 제거
- `V16__add_notification_outbox_timeline_index.sql`: Outbox 상태/채널 timeline 인덱스
- `V17__add_notification_outbox_dead_letter_index.sql`: dead-letter 최신순 운영 조회 인덱스
- `V18__add_attendance_request_idempotency_key.sql`: 학부모 출결 변경 요청의 requester별 멱등 키 보장

마이그레이션이 컬럼 제거처럼 되돌리기 어려운 변경이면 배포 전 DB 백업과 forward-fix SQL을 준비하고, 변경 배경과 운영 절차를 관련 `docs/guides/*` 문서에 남깁니다.

---

## 8. 테스트 전략

권장 우선순위:

1. Controller/API 변경: 통합 테스트 우선
2. 보안 변경: 성공/실패 권한 케이스 모두 추가
3. 성능 변경: 쿼리 수 또는 응답 시간 최소 1개 수치화

참고 테스트 클래스:

- `src/test/java/com/erp/api/*IntegrationTest.java`
- `src/test/java/com/erp/integration/PageAccessIntegrationTest.java`
- `src/test/java/com/erp/performance/*PerformanceStoryTest.java`

---

## 9. 문서화 규칙

기능/정책 변경 시 반드시 문서를 같이 갱신하세요.

- 현재/향후 구현 작업: 루트 `PLAN.md`
- 개발/실행/환경/배포 가이드: `docs/guides/*`

특히 성능 작업은 아래 순서를 지켜 기록합니다.

1. 재현 시나리오 정의
2. 개선 전 측정
3. 개선 적용
4. 개선 후 측정
5. 트레이드오프를 관련 가이드 또는 README에 문서화

---

## 10. 신규 기능 개발 체크리스트

1. 도메인 폴더에 controller/service/repository/dto/entity 배치
2. DTO 검증 어노테이션 적용
3. `@PreAuthorize` + `SecurityConfig` URL 권한 동시 확인
4. API 응답 `ApiResponse<T>` 유지
5. 필요 시 Flyway migration 추가
6. 통합 테스트 추가/수정
7. 루트 `PLAN.md`에서 완료된 작업 제거
8. 성능 영향이 있으면 전/후 수치 기록

---

## 11. 현재 코드베이스에서 인지할 포인트

- 일부 레거시/전환 구간에서 URL 단수/복수 혼용이 존재합니다.
- 일부 API/뷰가 병행되어 있어 컨벤션 통일 작업 여지가 있습니다.
- `kindergarten/select` 는 안내 성격이 남아 있는 화면입니다.
- 공지사항 API 컨트롤러에는 임시 주석이 남아 있으므로 수정 시 인증 주체 연계를 먼저 점검하세요.
- `local`/`demo`는 의도적으로 Swagger/OpenAPI와 app-port Prometheus를 열지만, `prod`는 기본적으로 닫혀 있어야 합니다.
- `DataLoader`와 local 전용 로그인 bootstrap은 `local profile + app.seed.enabled=true`일 때만 동작하도록 유지합니다.

---

## 12. 참고 문서

- 사용자 가이드: `docs/guides/user-guide.md`
- 프로젝트 개요: `README.md`
- 문서 인덱스: `docs/README.md`
- 현재/향후 구현 작업: 루트 `PLAN.md`
