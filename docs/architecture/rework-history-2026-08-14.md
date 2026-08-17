# KinderP 개편 작업 상세 기록

기준일: 2026-08-14  
대상 프로젝트: `kinderp/erp`  
목적: 오래된 유치원 ERP를 TownPet과 차별화되는 운영형·다중 테넌트 백엔드 포트폴리오로 재정비한 작업을 기능, 구조, 운영, 검증 관점에서 기록한다.

## 1. 이 문서의 범위와 시간 해석

이번 작업은 대화형 AI 개발 세션에서 진행된 전체 개편 작업을 기준으로 한다. Git에서 확인되는 대표 개편 커밋 범위는 다음과 같다.

- 시작 커밋: `c841847 refactor: 운영 감사와 계정 설정 화면 개편`
- 마지막 커밋: `a711a73 fix: 출결 시간 필드 attribute escaping 강화`
- Git 기록상 커밋 수: 98개
- 변경 파일: 223개
- 변경량: 추가 9,410줄, 삭제 7,039줄
- Git 커밋 시각 범위: 2026-08-14 18:23:28 ~ 21:07:58 (Asia/Seoul)

Git 커밋 시각은 실제 대화·분석·실행에 소요된 전체 시간을 측정하지 않는다. 따라서 “6시간”이라는 체감 작업 시간과 Git에 기록된 커밋 시간 범위는 동일한 지표가 아니다. 아래 내용은 추측이 아니라 현재 저장소의 커밋, 파일, 테스트 출력, 운영 문서를 근거로 정리했다.

## 2. 최종 결과 요약

이번 개편의 핵심은 화면을 새로 칠하는 수준이 아니라, 다음 네 가지 축을 동시에 정리한 것이다.

1. 화면의 inline JavaScript와 중복 템플릿 로직을 페이지 모듈로 이동했다.
2. 원장·교사·학부모가 사용하는 업무 흐름을 한국어 운영 용어와 상태 위계 중심으로 다시 정리했다.
3. 인증, tenant 경계, rate limit, CSP, Outbox, 백업·롤백 등 운영 실패 지점을 보강했다.
4. 성능·접근성·시간대·배포 준비도를 테스트와 문서로 설명할 수 있게 만들었다.

결과적으로 이 프로젝트의 포트폴리오 서사는 “유치원 CRUD 화면”에서 “권한·상태 전이·동시성·실패 복구를 통제하는 내부 운영 플랫폼”으로 이동했다.

## 3. 기능별 변경 내역

### 3.1 추가된 기능

#### 출결 변경 요청 멱등성

- `AttendanceChangeRequest`에 `Idempotency-Key` 기반 중복 요청 방어를 추가했다.
- 같은 키와 같은 요청 내용은 기존 요청을 재사용한다.
- 같은 키를 다른 payload로 재사용하면 충돌로 거부한다.
- DB migration `V18__add_attendance_request_idempotency_key.sql`을 추가했다.
- 학부모의 모바일 네트워크 재전송을 실제 운영 문제로 보고 API 통합 테스트를 보강했다.

#### 역할별 대시보드 출결 요약

- 원장용 대시보드가 이미 조회한 출결 데이터를 다시 요청하지 않도록 API 흐름을 재사용했다.
- 학부모용으로는 전체 일일 출결이 아니라 본인 자녀 범위의 `dashboard-summary` API를 추가했다.
- `AttendanceDashboardSummaryResponse`를 추가해 역할별 응답 범위를 분리했다.
- dashboard cache와 출결 summary를 연결해 반복 조회 비용을 줄였다.

#### 인증 rate limit

- login IP, login email, refresh IP, signup IP 단위 Redis rate limit을 설정 가능하게 만들었다.
- 제한 초과 시 429 응답과 `Retry-After`를 제공한다.
- `AuthRateLimitProperties`와 설정값 양수 검증을 추가했다.
- 인증 실패와 성공의 카운터 처리, 신뢰할 수 없는 forwarded header에 의한 우회 방어를 테스트했다.

#### CSP 및 보안 이벤트 위임

- nonce 기반 Content Security Policy를 적용했다.
- `unsafe-eval`에 의존하지 않는 Alpine CSP 방식으로 정리했다.
- 템플릿 inline event handler를 줄이고 `data-action` 이벤트 위임으로 이동했다.
- `script-src-attr 'none'` 계약을 유지하도록 정적 검사와 테스트를 추가했다.

#### 로컬 frontend asset build

- Tailwind CDN 의존성을 제거하고 저장소 내부 vendor asset과 빌드 산출물로 전환했다.
- `tailwind.config.js`, `tailwind.input.css`, `app.css`, `scripts/build-assets.mjs`를 정리했다.
- `npm run frontend:build`가 Tailwind CSS와 Alpine/HTMX/SweetAlert asset을 재현 가능하게 생성한다.

#### Notification Outbox 운영 기능

- 채널별 Outbox sender registry를 정리했다.
- 상태·채널·검색어·페이지 단위 운영 조회를 지원한다.
- DEAD_LETTER 목록과 retry API를 원장 전용 운영 기능으로 분리했다.
- 재시도, backoff, max attempts, dead-letter 전이와 전달 결과 메트릭을 연결했다.
- dead-letter 조회 성능을 위한 `V17__add_notification_outbox_dead_letter_index.sql`을 추가했다.
- incident webhook sender의 HMAC 서명 생성 계약을 통합 테스트에서 실제 JSON payload 기준으로 검증했다.

#### 백업·복구 데이터 assertion

- `restore-production-backup.sh`에 선택적인 MySQL assertion을 추가했다.
- `--mysql-assert-query`와 `--mysql-assert-expected`로 복구 후 SQL 결과를 비교한다.
- `--redis-assert-key`와 `--redis-assert-expected`로 Redis 복구 값도 비교할 수 있다.
- assertion 인자 쌍이 불완전하면 실행 전에 거부한다.
- disposable container 라벨과 `--confirm-disposable`를 유지해 운영 데이터 덮어쓰기를 막는다.

#### 배포 추적성과 SSH 신뢰 경계

- `APP_VERSION`에 이미지와 동일한 Git SHA를 주입한다.
- `/actuator/info`에서 실행 중인 앱 버전을 확인할 수 있다.
- 실패 rollback 시 이전 이미지뿐 아니라 이전 `APP_VERSION`도 함께 복원한다.
- CD의 `ssh-keyscan` 즉시 신뢰 방식을 제거했다.
- `DEPLOY_KNOWN_HOSTS` secret과 `StrictHostKeyChecking=yes`를 사용하도록 바꿨다.

#### reduced-motion 지원

- 사용자 운영 환경의 `prefers-reduced-motion` 설정을 존중한다.
- dashboard/home entrance animation이 reduced-motion 환경에서 즉시 표시되도록 했다.
- Playwright에서 실제 reduced-motion 동작을 확인했다.

### 3.2 기존 기능의 수정·개선

#### 화면 모듈화

기존 Thymeleaf 화면에 섞여 있던 큰 inline script와 반복 로직을 페이지별 JavaScript로 분리했다.

- 로그인·회원가입: `auth-forms.js`
- 프로필·계정 설정: `profile.js`, `settings-page.js`
- 원생 등록·수정·상세/학부모 관리: `kid-form.js`, `kid-detail.js`, `kids.js`
- 유치원 등록·선택: `kindergarten-create.js`, `kindergarten-select.js`
- 반 관리: `classrooms.js`
- 출결·출결 요청·월간 리포트: `attendance.js`, `attendance-requests.js`, `monthly-report.js`
- 감사 로그: `audit-log-page.js`로 인증 감사와 업무 감사의 공통 동작을 통합
- 공지·알림장: `announcement-list.js`, `announcement-editor.js`, `content-editor.js`, `notepad-list.js`, `notepad-write.js`
- 캘린더: `calendar-page.js`
- 대시보드: `dashboard.js`, `dashboard-home.js`
- Notification Outbox: `notification-outbox.js`

이 변경으로 템플릿은 구조와 서버 데이터 계약에 집중하고, fetch·상태·이벤트·escape 처리는 모듈로 이동했다.

#### 화면 디자인과 용어

- 운영 화면의 영문 eyebrow와 혼합 용어를 한국어로 정리했다.
- `Classroom Directory`를 `반 목록`, `Outbox Timeline`을 `전달 타임라인` 등 실제 사용자가 이해할 수 있는 용어로 바꿨다.
- 원장 dashboard의 “오늘의 운영”, “오늘 바로 처리할 업무” 등 업무 우선순위가 먼저 보이도록 위계를 조정했다.
- 버튼·필터·상태 badge의 최소 높이와 focus ring을 정리했다.
- 학부모 모바일 화면에서는 핵심 액션을 숨기지 않고 카드/리스트 형태로 전환했다.
- 운영 화면의 장식성 gradient, 과도한 카드 겹침, 장식용 요소를 줄이고 정보 스캔을 우선했다.

#### 시간대 일관성

- 서버의 날짜·시간 기준을 `Asia/Seoul`로 고정했다.
- `ProductTime`에 업무 날짜와 현재 시각을 모으고, entity auditing에도 연결했다.
- 도메인 코드에 남아 있던 직접적인 `LocalDate.now()`·`LocalDateTime.now()` 의존을 제거했다.
- 브라우저에도 `window.AppTime`을 추가해 서버 응답을 서울 시간으로 파싱·표시했다.
- 출결 날짜 이동, 오늘 날짜 input, 감사 로그·Outbox·dashboard 시각 표시를 동일 규칙으로 맞췄다.
- ProductTime 직접 호출 회귀를 CI 정적 검사로 방지했다.

#### 오류·로그·운영 관측성

- 사용자 화면에는 내부 예외 원문 대신 일반화된 오류 메시지만 노출하도록 정리했다.
- debug 브라우저 로그는 `data-debug="true"`일 때만 출력되도록 바꿨다.
- logback rolling policy와 보관 기간을 정리했다.
- correlation ID를 요청·응답·로그에 연결했다.
- readiness, health, info, Prometheus management surface의 노출 조건을 profile별로 분리했다.

#### 보안·권한

- principal/teacher/parent 권한을 controller URL과 service method 양쪽에서 확인했다.
- tenant 경계 테스트를 추가해 다른 유치원 데이터 접근을 차단했다.
- prod에서 seed, Swagger/OpenAPI, app-port Prometheus, 약한 JWT secret, insecure cookie, wildcard/non-HTTPS CORS를 fail-closed로 막았다.
- proxy 뒤 client IP 신뢰 범위를 설정 기반으로 제한했다.
- 공개 principal signup과 OAuth account lifecycle을 정리했다.

## 4. 삭제·대체·축소된 항목

이번 작업에서 비즈니스 핵심 기능을 무단으로 삭제한 것은 아니다. 삭제된 항목은 중복·미구현·보안상 불리한 구조를 제거하거나 새 구조로 대체한 것이다.

### 4.1 삭제된 중복 문서

- `docs/PLAN.md` 삭제
- `docs/PROGRESS.md` 삭제
- 현재/향후 작업의 단일 기준은 저장소 루트 `PLAN.md`로 통합했다.
- 완료 이력은 `docs/COMPLETED.md` archive와 기능별 문서에 남기고, active 작업과 섞지 않았다.

### 4.2 삭제된 legacy layout 템플릿

- `src/main/resources/templates/layout/base.html` 삭제
- `src/main/resources/templates/layout/default.html` 삭제
- `src/main/resources/templates/layout/header.html` 삭제
- 공통 레이아웃 의존을 현재 fragments/header/footer와 페이지별 명시적 구조로 대체했다.
- 이로 인해 오래된 layout inheritance와 새 화면 구조가 섞여 생기던 스타일·스크립트 충돌을 줄였다.

### 4.3 삭제·축소된 구현 방식

- 템플릿에 남아 있던 대형 inline JavaScript를 제거하고 페이지 모듈로 이동했다.
- 외부 Tailwind CDN 의존성을 제거했다.
- `Kindergarten` entity에 실제 구현되지 않은 API 7개를 제거했다.
- 중복 fetch와 대시보드의 불필요한 일일 출결 재조회 흐름을 제거했다.
- 알림 목록의 가짜 전체-row button 구조를 제거하고, 알림 본문과 읽음/삭제 액션을 분리했다.
- production에서 무조건 출력되던 브라우저 debug 로그를 제거하고 opt-in으로 축소했다.

### 4.4 추가하지 않고 폐기한 작업

다음은 검토했지만 프로젝트 규모와 사용자의 “과설계 지양” 원칙에 따라 추가하지 않았다.

- GitHub Actions workflow를 별도 lint framework로 다시 검사하는 자동화
- 외부 provider가 없는 상태에서 실제 provider sandbox를 흉내 내는 별도 서버
- 이미 충분한 정적 검사 위에 추가하는 중복 테스트 계층
- 실제 운영 트래픽 근거 없이 도입하는 복잡한 분산 아키텍처나 메시지 브로커
- 클라우드 자격증명 없이 성공한 것처럼 기록하는 가짜 배포·rollback 결과

## 5. 테스트와 검증 작업

### 5.1 실행된 핵심 검증

- `./gradlew --no-daemon check`
  - 최신 상태에서 `BUILD SUCCESSFUL in 3m 3s`
- `npm run frontend:build`
- `npm run accessibility:templates`
  - 37개 템플릿 검사
- `npm run security:inline-handlers`
- `npm run security:view-errors`
- `npm run security:product-time`
  - 247개 Java 파일 검사
- `npm run docs:links`
- `npm run impeccable:detect:json -- --fast`
  - 결과 `[]`
- `npm audit --omit=dev --audit-level=high`
  - `found 0 vulnerabilities`
- `npm run e2e:smoke`
  - 8개 Playwright 시나리오 통과
- `bash -n`으로 배포·백업·복구 shell script 문법 검사
- `git diff --check`

### 5.2 대표적으로 보강된 테스트

- tenant 경계와 역할별 page/API 접근 테스트
- 인증 rate limit, 429, `Retry-After` 테스트
- 출결 변경 요청 Idempotency-Key 성공·충돌 테스트
- workflow 상태 전이 허용/거부 테스트
- Notification Outbox 권한·검색·retry·dead-letter 테스트
- incident webhook HMAC payload 검증 테스트
- ViewEndpoint의 한국어 운영 화면 계약 테스트
- reduced-motion 브라우저 테스트
- 서울 시간대 날짜 input·timestamp 브라우저 테스트
- 알림 목록의 nested interactive 회귀 테스트
- dashboard/notepad/outbox performance smoke

### 5.3 운영형 검증

- local/demo profile 기동 확인
- production-like Docker image build 확인
- non-root image user `10001:10001` 확인
- MySQL·Redis·Spring Boot·Caddy production-like stack 기동
- readiness와 Caddy HTTPS smoke 확인
- graceful restart recovery 확인
- backup artifact checksum 확인
- disposable MySQL/Redis restore drill 확인
- rollback script의 이미지·APP_VERSION 추적성 확인

## 6. 성능 개선 결과

기존 “느릴 것 같다”는 추측 대신 같은 시나리오를 개선 전후로 비교할 수 있는 포트폴리오 증거를 정리했다.

| 대상 | 개선 전 | 개선 후 | 변경 이유 |
|---|---:|---:|---|
| 알림장 목록 | 22 queries / 17ms | 5 queries / 6ms | 읽음 수 N+1 제거와 집계 쿼리 전환 |
| Dashboard 통계 | 13 queries / 13ms | 5 queries / 5ms | 집계 쿼리 통합과 정확도 보정 |
| Dashboard 반복 조회 | 5 queries / 12ms | 0 queries / 0ms | `dashboardStatistics` Caffeine cache 적용 |
| Backend CI | 5m 28s | 대표 1m 14s | push quick check와 heavy quality 분리 |

추가로 Docker k6 시나리오에서 Notepad/Dashboard p95·p99와 오류율을 기록했으며, 측정 조건과 한계는 [`performance-methodology.md`](./performance-methodology.md)에 분리했다.

## 7. 문서·포트폴리오 보강

- README에 최신 frontend build, 접근성, Playwright, KST 시간대 계약을 반영했다.
- `portfolio-story.md`에 현재 증거와 남은 보완점을 구분했다.
- `interview-guide.md`, `risk-response.md`, `evidence-map.md`로 면접 질문과 코드·테스트 증거를 연결했다.
- deployment guide에 preflight, readiness, SHA traceability, rollback, backup/restore, SSH host key 운영 절차를 기록했다.
- resume/application 문서에 TownPet과의 차별화 포인트를 반영했다.
- 공통 개발 규칙과 실제 빌드 버전의 drift를 정리했다.

## 8. 현재 상태와 남은 작업

### 완료된 로컬 범위

- 운영형 화면 개편과 페이지 모듈화
- 한국어 정보 위계와 반응형 핵심 흐름
- 인증·tenant·CSP·rate limit 보안 보강
- 출결·신청·Outbox 상태 전이와 멱등성
- 대시보드·알림장 성능 개선
- KST 시간대 통일
- 로컬 asset build와 CI 연결
- backup/restore/rollback/preflight 운영 자산
- 테스트·문서·포트폴리오 증거 정리

### 아직 완료로 표시하지 않은 외부 범위

- 실제 HTTPS 도메인과 클라우드 배포
- 운영 RDS/MySQL·Redis 접속 및 실제 backup/restore
- 실제 배포 URL smoke와 rollback 실패 주입
- OAuth 운영 redirect URI 검증
- 외부 notification provider sandbox 발송·실패·재시도·rate limit 검증
- Alertmanager 또는 실제 incident 수신 채널 확인

이 항목들은 코드만 수정해서 완료할 수 없고 cloud 계정, DNS/TLS, 운영 secret, provider 계정이 필요하다. 따라서 현재 저장소는 “운영 준비 자산과 로컬 production-like 검증 완료, 실제 외부 운영 증거 미확보”로 표현하는 것이 정확하다.

## 9. 커밋 추적 기준

세부 구현은 다음 대표 커밋 묶음에서 확인할 수 있다.

| 영역 | 대표 커밋 |
|---|---|
| 전체 화면 모듈화 | `c841847`, `079022f`, `facdcae`, `e765aa5`, `a67a257`, `e539830`, `9ff2f7c`, `7793190`, `653482b`, `c94e72b` |
| 인증·출결 UX | `75904fa`, `9223562`, `88d6484` |
| 보안·권한·멱등성 | `bc1eb65`, `0d90839`, `bb7820e`, `87e1ca5` |
| Outbox 운영 | `66bc41a`, `b93c7ca`, `5daceed`, `b773fb4` |
| 백업·배포·복구 | `949bc74`, `677d2f7`, `51652a7`, `99a3976`, `cfc7d4b`, `a40fdb1`, `9b8bfee`, `78d7ff7` |
| 시간대·성능 | `a5b6094`, `a8e7515`, `c0088ec`, `3dba46a`, `1dc28bd`, `ef1c76a` |
| UI polish·접근성 | `e9b2d30`, `47cf65c`, `b94b3b6`, `c4fe517`, `e708cea`, `5d15e4e`, `a711a73` |
| 문서·포트폴리오 | `f72015a`, `68c89e2`, `65ff742`, `4caa692`, `8ad66eb`, `c1fb67f` |

전체 원문은 다음 명령으로 확인할 수 있다.

```bash
git log --date=iso --pretty=format:'%h%x09%ad%x09%s' c841847^..HEAD
git diff --stat c841847^..HEAD
```

## 10. 한 문장 결론

이번 6시간의 핵심 산출물은 기능을 무작정 늘린 것이 아니라, 기존 화면과 서버를 운영 가능한 모듈·권한·상태 전이·실패 복구·성능 증거·배포 준비 자산으로 재구성한 것이다. 실제 클라우드와 외부 provider만 아직 연결되지 않았으며, 그 부분을 완료했다고 과장하지 않는 상태가 현재의 정확한 품질 판정이다.

## 11. 두 프로젝트의 이메일 경계를 Resend SMTP로 통일했다

TownPet은 이메일 인증·비밀번호 복구를 위해 SMTP를 사용하고 있었지만, ERP의 `EmailNotificationSender`는 구현되어도 production profile에서 `enabled=false`였다. 같은 VPS에 배포되는 두 프로젝트의 운영 기능을 다르게 끄면 알림 Outbox가 성공처럼 보이거나 기본 이메일 기능을 조용히 잃을 수 있으므로, ERP도 Resend SMTP를 사용하도록 production 설정을 바꿨다. SMTP host·credential·STARTTLS·발신 주소가 없으면 netcup 환경 validator가 기동 전에 거부한다.

- 근거: `src/main/resources/application-prod.yml`, `deploy/.env.netcup.example`, `scripts/validate-netcup-env.sh`, `docs/guides/netcup-deployment.md`
- 검증: ERP backend compile, Compose config, shell 문법과 문서 링크 검사를 다시 실행했다. 실제 Resend 수신·SPF/DKIM/DMARC는 netcup 승인 후 외부 검증으로 남아 있다.
- trade-off·한계: 이메일 기능은 완성되지만 Resend provider 장애가 알림 전달에 영향을 줄 수 있으므로 Outbox retry/dead-letter를 유지하고, 실제 provider sandbox에서 실패·재시도를 추가 확인한다.

2026-08-16 Docker Desktop을 실행한 뒤 전체 ERP 테스트를 다시 실행했고 304개 테스트가 2분 56초 만에 통과했다. 이전 Docker daemon 부재 실패는 환경 원인이었으며, 현재는 Testcontainers 기반 통합 테스트까지 로컬에서 확인된 상태다.

## 12. 공용 edge와 내부 Caddy 사이의 Host 계약을 명시했다

ERP 내부 Caddy는 TLS를 직접 종료하지 않고 `http://{$APP_DOMAIN}` site block으로 동작한다. 공용 edge가 upstream 기본 Host를 전달하면 이 site block과 매칭되지 않을 수 있으므로, 내부 reverse proxy에도 원래 요청 Host를 전달하도록 고정했다. TownPet의 media 경로와 함께 두 프로젝트가 하나의 edge를 공유하는 실제 토폴로지에서 발견한 배포 전 결함이다.

- 근거: `deploy/Caddyfile.netcup`, `kinderp/erp/deploy/Caddyfile.netcup`, TownPet `deploy/compose/Caddyfile.netcup`
- 검증: Compose config와 shell 문법·문서 링크 검사를 통과했다. 실제 HTTPS·로그인·media 요청은 netcup 승인 후 실행한다.
