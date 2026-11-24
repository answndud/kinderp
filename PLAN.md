# PLAN.md

## Goal

포트폴리오 전체 리뷰에서 나온 결함을 우선순위대로 고친다. 이 저장소는 README에서 "권한 경계·동시성 제어·실패 복구"를 내세우므로, 그 주장과 실제 코드·테스트·CI가 일치하게 만드는 것이 완료 조건이다.

---

## 0. 작업 에이전트 필수 규칙 (모든 Slice 공통, 반드시 먼저 읽을 것)

1. **한 번에 한 Slice만** 진행한다. 아래 순서를 지킨다. 다음 Slice로 넘어가기 전에 현재 Slice의 "검증"을 모두 통과시킨다.
2. 각 Slice 시작 전 해당 Slice의 "대상 파일"을 **직접 읽고** 이 문서의 줄 번호·메서드명이 현재 코드와 맞는지 확인한다. 다르면 코드가 기준이며, 차이를 사용자에게 보고한다.
3. 설계·범위 결정은 이 문서에 **이미 내려져 있다.** 사용자에게 다시 묻지 말고 그대로 실행한다. 멈추고 보고하는 경우는 두 가지뿐이다: 이 문서의 전제가 실제 코드와 다를 때, 그리고 각 Slice에 "멈춤 조건"으로 적힌 상황이 생겼을 때.
4. 다음 사용자 소유 dirty 파일은 **내용을 수정하거나 변경분을 stage하지 않는다.** 예외는 Slice 12의 추적 해제(`git rm --cached`)다. 이 작업은 로컬 파일 내용을 보존하므로 허용한다.
   - `docs/04-데모/로컬-개발-워크플로.md`
   - `docs/cli환경에서 실행.md`
   - `docs/guides/local-demo-accounts.md`
4-1. **보관 폴더 규칙 (Slice 1·12 공통):** 공개 저장소에서 빼는 파일은 삭제하지 않고, 루트의 git 무시 폴더 `archive/`로 옮긴다. 원래 경로 구조는 그대로 유지한다. 절차:
   ```bash
   git rm -r --cached "<경로>"
   mkdir -p "archive/$(dirname "<경로>")"
   mv "<경로>" "archive/<경로>"
   ```
   `.gitignore`에는 `/archive/` 한 줄만 추가한다. 파일별로 ignore 항목을 늘리지 않는다.
5. 커밋은 AGENTS.md 10절을 따른다: 변경 요약 → 커밋 메시지 후보 제시 → **사용자 승인 후** 해당 Slice 파일만 `git add <파일>`로 stage → commit. `git add -A`/`git add .` 금지. push·force push·rebase·`--no-verify` 금지.
6. 파일 수정은 Edit/Write 도구로 한다 (CLAUDE.md의 `apply_patch`는 Codex 전용 표기다).
7. 시간은 `ProductTime`을 사용한다. `LocalDateTime.now()`/`LocalDate.now()` 직접 호출 금지 (`npm run security:product-time`이 검사한다).
8. 비즈니스 오류는 `BusinessException(ErrorCode)`로 던진다. 컨트롤러에서 `ResponseEntity.badRequest().body(...)`를 새로 만들지 않는다.
9. Gradle은 **Java 21**로 실행한다. 기본 `java`가 25 이상이면 `Unsupported class file major version`으로 실패하므로, 먼저 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`를 실행한다. 통합 테스트는 Docker가 필요하다. 테스트 전에 `docker info`로 Docker가 떠 있는지 확인하고, 안 떠 있으면 사용자에게 Docker Desktop 실행을 요청하고 멈춘다.
10. 테스트가 실패하면 테스트를 지우거나 `@Disabled`로 막지 않는다. 원인을 고치거나, 고칠 수 없으면 실패 로그와 함께 사용자에게 보고한다.
11. Slice를 끝내면 이 PLAN.md에서 해당 Slice를 **삭제**한다 (AGENTS.md 12절). 완료 기록은 남기지 않는다.

공통 검증 명령 (각 Slice 마지막에 항상 실행):

```bash
./gradlew --no-daemon compileJava compileTestJava
./gradlew --no-daemon fastTest
```

단일 통합 테스트 실행 예:

```bash
./gradlew --no-daemon test --tests "com.kinderp.api.KidApiIntegrationTest"
```

---

## Slice 8 — 작은 정합성 결함 묶음

각 항목은 서로 독립적이다. 항목마다 테스트를 추가하고 통과시킨 뒤 다음 항목으로 넘어간다.

**8-1. 동시 출결 upsert 충돌이 500으로 나감**
- `AttendanceService` 406~409줄 부근은 check-then-insert다. `uk_kid_date` 위반 시 `DataIntegrityViolationException`이 그대로 올라간다.
- `GlobalExceptionHandler`에 `DataIntegrityViolationException` 핸들러를 추가하고 409를 반환한다. 이를 위해 ErrorCode `DUPLICATE_RESOURCE`(CONFLICT)를 추가하거나, 같은 의미의 기존 코드를 재사용한다. 응답 메시지에 예외 원문을 넣지 않는다.
- 테스트: 같은 kid·date로 출결 생성 2건을 동시에 보내면 결과가 200·409(또는 200·200 upsert)이고 500은 없다.

**8-2. 대시보드 캐시 무효화 시점·누락**
- `DashboardService.evictDashboardStatisticsCache`가 쓰기 트랜잭션 **안에서** 즉시 실행된다. 커밋 전에 다른 요청이 오래된 값으로 캐시를 다시 채울 수 있다.
- 변경:
  - 캐시를 무효화하는 쪽은 `DashboardCacheEvictRequested(Long kindergartenId)` record 이벤트를 `ApplicationEventPublisher`로 발행한다.
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` 리스너가 `CacheManager`로 evict한다.
  - 기존 `evictDashboardStatisticsCache` 호출부는 `grep -rn "evictDashboardStatisticsCache" src/main`으로 모두 찾아 이벤트 발행으로 바꾼다.
- 누락된 무효화도 추가한다: `KidService`의 원생 생성·삭제·반 변경, 회원 승인·탈퇴(`totalKids`·교사 수 통계에 영향을 주는 곳). `DashboardStatisticsResponse` 필드를 보고 어떤 변경이 통계에 영향을 주는지 판단한다.
- 테스트: 원생 추가 후 대시보드 통계 API의 `totalKids`가 즉시 증가하는지 확인한다 (`DashboardApiIntegrationTest`).
- Caffeine 대신 Redis 캐시로 바꾸는 작업은 **하지 않는다.** 단일 인스턴스라는 전제를 `docs/architecture/performance-methodology.md` 한계 절에 한 줄로 적는다.

**8-3. bulk 출결 N+1**
- `AttendanceService` 133~149줄은 원생마다 `kidService.getKid`, `getRequester`, `findByKidIdAndDate`를 호출한다.
- 요청자는 루프 밖에서 1회 조회한다. 원생은 `kidRepository.findAllById(kidIds)`로 한 번에 가져와 권한을 검증한다. 기존 출결은 `attendanceRepository.findByKidIdInAndDate(kidIds, date)`(없으면 추가)로 한 번에 가져와 Map으로 매칭한다.
- 테스트: `performance/NotepadPerformanceStoryTest`처럼 Hibernate `Statistics`로 원생 10명 bulk 요청의 쿼리 수를 측정한다. 원생 수와 무관하게 상수(예: ≤ 8)임을 assert한다. 변경 전 쿼리 수를 먼저 측정해서 PR 설명용으로 사용자에게 보고한다 (AGENTS.md 11절).

**8-4. OSIV OFF 위반: 엔티티가 컨트롤러로 나감**
- `AttendanceService.getAttendance()`가 엔티티를 반환하고, `AttendanceController` 120~123줄이 트랜잭션 밖에서 `toResponse(entity)`로 `getKid().getName()`을 읽는다.
- 서비스가 응답 DTO를 반환하도록 바꾼다. 같은 패턴을 찾아 함께 고친다:
  ```bash
  grep -rn "toResponse(\|Response.from(" src/main/java/com/kinderp/domain/*/controller
  ```
  너무 많으면(10곳 초과) 출결 도메인만 고치고 나머지 목록을 사용자에게 보고한다.

**검증:**
```bash
./gradlew --no-daemon test --tests "com.kinderp.api.AttendanceApiIntegrationTest" --tests "com.kinderp.api.DashboardApiIntegrationTest" --tests "com.kinderp.performance.*"
```

---

## Slice 9 — QueryDSL 실제 사용 (기술 스택 주장과 일치시키기)

**왜:** `JPAQueryFactory`는 `global/config/QuerydslConfig.java`에서 빈으로 등록만 돼 있고 domain 코드 사용처가 0건이다. README·AGENTS.md에는 QueryDSL을 기술 스택으로 적고 있다. 대신 `NotificationOutboxRepository` 92~121줄에 `(:status IS NULL OR ...)` 형태의 JPQL 동적 쿼리가 있다.

**작업 순서:**
1. `NotificationOutboxRepository`의 동적 검색 쿼리(운영 화면 검색)를 custom repository로 옮긴다: `NotificationOutboxQueryRepository` 인터페이스 + `NotificationOutboxQueryRepositoryImpl`(JPAQueryFactory 사용). 파라미터별 `BooleanExpression` 메서드가 null이면 조건을 생략하게 한다.
2. 기존 시그니처를 쓰는 서비스 호출부를 새 메서드로 바꾸고, 예전 JPQL 메서드는 삭제한다.
3. count 쿼리를 분리한다 (`PageableExecutionUtils.getPage`).
4. `LIKE '%kw%'` 조건은 기능상 유지한다. 인덱스를 못 탄다는 한계를 메서드 주석 한 줄로 남긴다.
5. `NotificationOutboxOpsApiIntegrationTest`의 검색 테스트가 그대로 통과해야 한다. 필터 조합 테스트(조건 없음 / status만 / keyword만 / 전부)가 없으면 추가한다.
6. `ArchUnit LayerDependencyTest`가 새 Impl 클래스 위치를 허용하는지 확인한다.

**검증:**
```bash
./gradlew --no-daemon test --tests "com.kinderp.api.NotificationOutboxOpsApiIntegrationTest" --tests "com.kinderp.architecture.LayerDependencyTest" --tests "com.kinderp.performance.NotificationOutboxPerformanceSmokeTest"
```

---

## Slice 10 — 코드 위생 정리

동작 변경 없는 정리만 한다. 항목마다 컴파일하고 fastTest를 실행한다.

1. 빈 패키지 `src/main/java/com/kinderp/domain/dto/`를 삭제한다. 파일이 0개임을 먼저 확인한다.
2. `AttendanceChangeRequestService`, `AttendanceService`, `KidService` 등에 인라인으로 반복된 FQCN(`com.kinderp...`, `java.util.stream.Collectors`, `org.springframework.data.domain.Page`)을 import로 정리한다. 대상은 `grep -rn "com\.kinderp\.\|java\.util\.\|org\.springframework\." src/main/java --include=*.java | grep -v "^.*:import \|^.*:package "`로 찾는다. 변경 파일은 최대 10개로 하고, 나머지는 보고한다.
3. `KidApplicationService`의 approve/offer/reject는 `KidApplicationReviewService`로 위임만 한다. 이 위임 메서드들의 중복 `@Transactional`을 제거한다. 위임받는 쪽에 `@Transactional`이 있는지 먼저 확인한다.
4. `global/config/DataLoader.java`의 `@Profile("local | prod")`. 이 부분은 결정이 끝났다: **prod 공개 데모 시드는 유지하되, 예외를 명시적이고 좁게 만든다.**
   - 근거: `scripts/validate-netcup-env.sh` 34줄이 netcup 배포에 `APP_PUBLIC_DEMO_ENABLED=true`를 **필수**로 요구한다. 즉 실제 공개 데모(kinderp.cloud)는 이 시드로 돌아간다. 제거하면 데모가 빈 화면이 된다. 일반 운영 예시 `deploy/.env.prod.example`은 `false`다.
   - 변경 A: `StartupSafetyValidator` 61~64줄에 조건을 추가한다. prod에서 `app.seed.enabled=true`이고 `APP_PUBLIC_DEMO_ENABLED=true`이면 통과시키되, **`log.warn`으로 "PUBLIC DEMO MODE: seeded demo accounts are active"를 기동 로그에 1회** 남긴다. 숨은 예외가 아니라 눈에 보이는 모드로 만드는 것이다.
   - 변경 B: `StartupSafetyValidatorTest`에 세 케이스가 있는지 확인하고 없으면 추가한다: prod+seed+demo=false → 기동 실패, prod+seed+demo=true → 통과, prod+seed=false → 통과.
   - 변경 C: `DataLoader`가 재기동 때 데이터를 중복 생성하지 않는지 확인한다 (멱등). 이미 존재 여부를 검사하는 코드가 있으면 그대로 두고, 없으면 "principal 계정 존재 시 skip" 가드를 맨 앞에 추가한다.
   - 변경 D: `docs/guides/env-contract.md`의 prod 절에 "공개 데모 모드" 하위 항목을 3~4줄로 추가한다. 내용: 어떤 변수 조합에서 켜지는지, 일반 운영 배포에서는 반드시 `false`라는 것, 시드 계정 비밀번호가 공개돼 있다는 것.
   - `@Profile` 값은 바꾸지 않는다.
5. 다음은 **하지 않는다:**
   - 두 `ApplicationStatus` enum의 이름 변경 (영향이 크고 이득이 작음)
   - `global/debug/DebugDbController` 삭제 (local 전용으로 가드돼 있음)

---

## Slice 11 — README 정직성·첫인상

**결정된 사항:**
- **원본 출처:** 저장소 어디에도 원본 팀 저장소 링크가 없으므로 **링크 없이** 사실만 쓴다. 사용자가 확인해 준 사실은 "백엔드 부트캠프 4인 팀 프로젝트를 혼자 AI 코딩 에이전트로 재작성"까지다. 부트캠프 이름, 기간, 원본에서 맡은 파트는 **지어내지 않는다.**
- **AI 활용:** 숨기지 않고 명시한다. 면접에서 "AI로 짰나요?"라는 질문을 받았을 때 이미 README에 적혀 있으면 방어가 아니라 설명이 된다. 핵심 메시지는 "에이전트에게 구현을 맡기고, 설계 결정·리뷰·검증 기준은 직접 정했다"이다.
- **데모 URL:** 공개 데모를 README 최상단에 넣는다. 데모 계정은 README에 이미 공개돼 있으므로 그대로 쓴다. 단, 넣기 전에 실제로 응답하는지 확인한다(아래 1번).

**작업 순서:**
1. 데모 응답을 확인한다:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://kinderp.cloud/
   ```
   - 200 또는 302면 2번으로 간다.
   - 응답이 없거나 5xx면 데모 링크는 넣지 않고 이 결과를 보고한 뒤 3번부터 진행한다.
2. README 제목과 한 줄 소개 바로 아래, 배지보다 위에 다음 블록을 넣는다:
   ```markdown
   > **Live demo:** https://kinderp.cloud — 아래 [로컬 실행](#로컬-실행)의 데모 계정으로 로그인할 수 있습니다. 공개 데모이므로 데이터는 다른 방문자와 공유됩니다.
   ```
3. "한눈에 보기" 절 바로 앞에 `## 프로젝트 배경` 절을 추가한다. 아래 문안을 기준으로 쓰되, 괄호 안 항목은 실제로 완료된 Slice만 남긴다:
   ```markdown
   ## 프로젝트 배경

   백엔드 부트캠프에서 4인 팀으로 만든 유치원 관리 프로젝트를 출발점으로, 혼자 처음부터 다시 설계하고 재작성했습니다.
   재작성에는 AI 코딩 에이전트(Codex, Claude Code)를 적극 활용했습니다. 구현의 상당 부분은 에이전트가 작성했고, 저는 다음을 직접 결정하고 검증했습니다.

   - **도메인 규칙과 상태 전이:** 입학 신청 대기열·정원·만료, 출결 변경 요청 승인 흐름을 엔티티 메서드와 DB 제약으로 강제
   - **권한 모델:** 유치원(tenant)·역할·소유권 검증을 `AccessPolicyService`로 모으고, 같은 유치원 안 학부모 간 정보 노출(IDOR)을 자체 리뷰로 찾아 수정
   - **동시성·신뢰성:** 비관적 락·멱등 키·Outbox claim token fencing, 동시 요청 통합 테스트로 검증
   - **검증 기준:** Testcontainers(MySQL·Redis) 통합 테스트와 쿼리 수 회귀 테스트를 CI에서 실행
   ```
4. "한눈에 보기" 표의 `역할` 행을 `1인 재작성 — 설계·구현·테스트·배포 (원본: 부트캠프 4인 팀 프로젝트)`로 바꾼다.
5. "핵심 결과"의 `Backend CI: 5분 28초 → 1분대` 줄을 `Backend CI: 빠른 검사(fast)와 Testcontainers 통합 테스트 job을 분리해 push·PR마다 실행`으로 바꾼다. 측정하지 않은 숫자는 쓰지 않는다.
6. "핵심 결과" 끝에 한 줄을 추가한다: `- 동시성 통합 테스트: 반 정원 동시 승인, Offer 만료 vs 수락, refresh token 동시 재사용, Outbox 중복 claim`. 실제로 추가된 테스트만 적는다.
7. 기술 스택의 QueryDSL 표기는 Slice 9가 끝났을 때만 유지한다. 끝나지 않았으면 제거한다.
8. "검증" 절의 명령 순서를 백엔드 우선으로 바꾼다: `fastTest` → `integrationTest` → 프론트 검사.
9. "더 보기" 표는 Slice 12에서 남기는 문서만 가리키게 한다. Slice 12 이후 한 번 더 확인한다.

**검증:**
```bash
npm run docs:links
```

**완료 조건:** README 첫 화면에서 데모 링크, 프로젝트 출처(팀 → 1인 재작성, AI 활용), 본인 결정 사항이 보이고, 사실과 다른 수치가 없다.

---

## Slice 12 — 문서·에이전트 파일 축소 (맨 마지막)

**결정된 원칙:**
- 공개 저장소에 남기는 문서는 "면접관이 10분 안에 읽을 가치가 있는 것"과 "저장소를 실행·운영하는 데 필요한 것"만이다.
- 빼는 문서는 0절 4-1 규칙대로 `archive/`로 옮긴다. 삭제하지 않는다.
- **AGENTS.md·CLAUDE.md는 남긴다.** README에 AI 활용을 밝혔으므로, 에이전트에게 준 작업 규칙은 숨길 대상이 아니라 "AI를 어떻게 통제했는지" 보여주는 증거다. PLAN.md는 마지막에 `No active work`로 비운다(AGENTS.md 12절).
- `.agents/skills/**`는 외부 디자인 스킬을 복사해 둔 것이라 이 프로젝트 고유 산출물이 아니다. 보관 처리한다.
- `blog/`는 01~25편을 남긴다. 도메인·인증·Outbox·성능을 다룬 기술 글은 학습 깊이를 보여준다. 계획 메모(00_*)와 취업 패키지·점수 매기기 글(26, 27)은 보관 처리한다.
- CD workflow·`deploy/` 중복 정리는 실제 배포 경로를 건드리므로 **이번 계획에서 하지 않는다** (Backlog).

**1) `archive/`로 옮길 파일 (0절 4-1 절차):**
- 루트:
  - `.agents/`
  - `.impeccable.md`
  - `BLOG_PLAN.md`
  - `BLOG_PROGRESS.md`
  - `CURRENT_FEATURES.md`
  - `HELP.md`
- `docs/` 한글 번호 폴더 전체:
  - `docs/01-프로젝트-기획/`
  - `docs/02-설계/`: 단, `설계-의사결정-기록.md`는 2)에서 먼저 옮기고 나머지만 보관한다.
  - `docs/03-성능-보안-운영/`
  - `docs/04-데모/`: dirty 파일 `로컬-개발-워크플로.md` 포함. `git rm --cached`는 로컬 내용을 보존한다.
  - `docs/05-차별화/`
  - `docs/06-문서-감사/`
  - `docs/07-보안/`
- docs 기타:
  - `docs/문서-안내.md`
  - `docs/cli환경에서 실행.md` (dirty, 로컬 절대경로 노출)
  - `docs/COMPLETED.md`
  - `docs/architecture/rework-history-2026-08-14.md`
- `docs/guides/`:
  - `evidence-map.md`
  - `risk-response.md`
  - `production-like-checklist.md`
  - `user-guide.md`
- `blog/`:
  - `00_quality_checklist.md`
  - `00_rebuild_guide.md`
  - `00_series_plan.md`
  - `26-demo-architecture-and-interview-pack.md`
  - `27-spring-boot-refactoring-doctor-100.md`

**2) 옮겨서 살릴 파일:**
- `git mv "docs/02-설계/설계-의사결정-기록.md" docs/architecture/decision-log.md`
- 제목은 `# Architecture Decision Log`로 바꾼다. 본문에 TownPet, 면접, 이력서를 언급하는 문장이 있으면 그 문장만 삭제한다.

**3) 남는 문서 (최종 목록, 이 외에 docs에 남는 `.md`가 있으면 보고):**
- `docs/README.md`: 유일한 문서 인덱스. 아래 4)에서 다시 쓴다.
- `docs/architecture/`:
  - `portfolio-story.md`
  - `performance-methodology.md`
  - `api-access-matrix.md`
  - `workflow-state-machine.md`
  - `decision-log.md`
- `docs/guides/`:
  - `developer-guide.md`
  - `env-contract.md`
  - `netcup-deployment.md`
  - `deployment-guide.md`
  - `secret-incident-response.md`
  - `demo-scenario.md`
  - `local-demo-accounts.md` (dirty, 내용 수정 금지)
- `deployment-guide.md`는 `.github/workflows/cd.yml`·`scripts/`에서 참조되면 남기고, 참조가 없으면 1)에 추가해 보관한다. 확인 명령:
  ```bash
  grep -rn "deployment-guide" .github scripts deploy
  ```
- 위 목록의 문서 본문에 "TownPet", "면접", "이력서", "interview"가 나오면 그 문장·절만 삭제한다. 확인 명령:
  ```bash
  grep -rln "TownPet\|타운펫\|면접\|이력서\|interview" docs README.md blog
  ```

**4) 인덱스와 규칙 파일 정리:**
- `docs/README.md`를 다시 쓴다. 3)의 문서마다 한 줄 설명을 붙이고, `문서-안내.md`와 보관된 문서 링크를 모두 제거한다. 30줄 이내로 쓴다.
- `blog/README.md`에서 00_*, 26, 27 항목을 제거한다.
- `AGENTS.md`:
  - 6절의 Impeccable 관련 줄(`.impeccable.md`, `.agents/skills/*`, `npm run impeccable:detect`, `$audit`/`$critique`/`$polish`)을 삭제한다. "운영형 ERP 화면은…" 문단은 유지한다.
  - 1절과 5.7절의 QueryDSL 표현은 Slice 9의 결과와 맞춘다.
- `CLAUDE.md`: "변경은 `apply_patch`로 수행하고" 문구를 "변경은 작은 단위로 수행하고"로 바꾼다.
- `package.json`: `impeccable:detect`, `impeccable:detect:json` 스크립트를 제거하고, `scripts/impeccable-detect.mjs`도 `archive/scripts/`로 옮긴다. `.github/workflows`에서 이 스크립트를 호출하는지 먼저 grep으로 확인한다.
- `PLAN.md`: 모든 Slice가 끝났으면 내용을 `# PLAN.md` + `No active work` + 아래 Backlog 절만 남긴다.

**검증:**
```bash
npm run docs:links
npm run frontend:build
./gradlew --no-daemon fastTest
git ls-files | grep -E '\.md$' | grep -v '^blog/' | sort    # 3)의 목록 + 루트 README/AGENTS/CLAUDE/PLAN만 나와야 한다
git check-ignore archive/HELP.md                              # 경로가 출력되어야 한다
```

**완료 조건:** 추적되는 문서가 3)의 목록과 일치하고, 깨진 링크가 없으며, 보관한 파일이 모두 로컬 `archive/`에 남아 있다.

---

## Backlog (이번 계획 범위 밖)

- 기존 netcup 배포 외부 증거 확보(SMTP 실수신, 암호화 백업 외부 보관, 이미지 롤백, provider sandbox). 상세 내용은 `git show HEAD:PLAN.md`의 P2/P3를 참고한다.
- 로그인 rate limit을 이메일 단독 키에서 IP+이메일 조합 + 점진적 지연으로 바꿔 계정 잠금 DoS를 완화한다.
- 소셜 로그인 성공 핸들러에서 INACTIVE/삭제 회원 명시 차단 (`OAuth2AuthenticationSuccessHandler` 78~85줄).
- 감사 로그 CSV export에 수식 인젝션 방어(`= + - @`로 시작하는 셀에 `'` 접두) 추가 (`AuthAuditLogQueryService` 169~177줄).
- 생성 POST 201 응답, 컨트롤러의 `badRequest().body(...)`를 예외로 통일, `/kids`와 `/kids/page` 이원화 정리.
- `JwtFilter`의 요청당 JWT 중복 파싱 축소, 만료 토큰 로그 레벨 ERROR → DEBUG.
- `ProductTime` static을 `Clock` 주입으로 전환 (시간 의존 테스트 용이성).
- 공개 데모 데이터 주기적 초기화(예: 매일 새벽 시드 재적재). 방문자가 데모 데이터를 훼손할 수 있음.
- CD workflow(`cd.yml`, `cd-netcup.yml`, `container-publish.yml`)와 `deploy/` compose·Caddy 설정을 실제 사용하는 netcup 경로 하나로 통합하고, 남는 workflow의 action을 SHA로 고정.
