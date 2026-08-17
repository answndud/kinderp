# Demo Scenario Runbook

기준일: 2026-08-14

이 문서는 KinderP를 면접/시연에서 빠르게 보여주기 위한 클릭 순서와 기대 화면을 정리합니다.

## 1. 실행 전제

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

- `demo` 프로파일은 `local` 설정을 포함합니다.
- 시드 데이터는 자동 활성화됩니다.
- 기존 demo DB에 seed 계정이 이미 있어도 최근 시연용 샘플은 누락분만 보강합니다.
- Swagger/OpenAPI와 app-port Prometheus는 demo에서만 열립니다.

## 2. Demo 계정

| 역할 | 계정 | 비밀번호 | 먼저 볼 화면 |
| --- | --- | --- | --- |
| 원장 | `principal@test.com` | `test1234!` | `/dashboard`, `/applications/pending`, `/notification-outbox` |
| 교사 | `teacher1@test.com` | `test1234!` | `/attendance`, `/attendance-requests`, `/calendar` |
| 학부모 | `parent1@test.com` | `test1234!` | `/applications/pending`, `/notifications`, `/notepad` |

## 3. 5분 시연

1. 원장으로 로그인합니다.
2. `/dashboard`에서 출석/회원/운영 지표가 비어 있지 않음을 보여줍니다.
3. `/applications/pending`에서 `PENDING`, `WAITLISTED`, `OFFERED` 상태가 함께 보이는 검토 큐를 설명합니다.
4. `/notification-outbox`에서 timeline, 상태/채널/검색 필터, dead-letter retry 버튼을 보여줍니다.
5. `/audit-logs`, `/domain-audit-logs`에서 reason/summary 필터와 CSV export 경로를 보여줍니다.

## 4. 10분 시연

1. 원장 로그인: `principal@test.com / test1234!`
2. 신청 처리 큐: 입학 신청, 대기열, offer 상태가 같은 큐에서 관리되는 구조를 설명합니다.
3. 알림 Outbox 운영: timeline을 상태/채널/검색어로 좁혀 보고 dead-letter 하나를 retry합니다.
4. 캘린더: 유치원 전체 일정, 반 반복 일정, 개인 운영 점검 일정이 함께 조회되는 구조를 설명합니다.
5. 감사 로그: 인증 감사 로그와 업무 감사 로그가 분리된 이유를 설명합니다.
6. README 성능 표: Notepad/Dashboard query count와 CI 시간 단축 수치를 설명합니다.
7. Swagger: `/swagger-ui.html`에서 운영 API 설명을 보여줍니다.

## 5. 화면별 기대 데이터

| 화면 | 기대 데이터 | 비어 있을 때 우선 확인 |
| --- | --- | --- |
| `/dashboard` | 원생/교사/학부모 수, 오늘 출석 지표, 최근 운영 지표 | principal 계정의 kindergarten 연결, 기본 원생 seed |
| `/applications/pending` | `PENDING`, `WAITLISTED`, `OFFERED` 입학 신청 샘플 | parent1~3 계정, 해바라기반/장미반, `kid_application` 테이블 |
| `/notification-outbox` | `APP`, `PUSH`, `EMAIL` outbox timeline, 상태/채널/검색 필터, retry 버튼 | principal 계정, `notification`, `notification_outbox` 테이블 |
| `/calendar` | 유치원 전체 일정, 해바라기반 반복 일정, 개인 운영 점검 일정 | 해바라기 유치원/해바라기반, `calendar_events` 테이블 |
| `/audit-logs` | 로그인/refresh/social link/unlink 인증 이벤트, reason 필터 | `auth_audit_logs` 테이블, principal tenant 필터 |
| `/domain-audit-logs` | 입학 대기열/offer/승인 업무 변경 이력, summary 필터 | `domain_audit_logs` 테이블, 입학 신청 샘플 |
| `/swagger-ui.html` | Auth, Attendance, Dashboard, Audit, Outbox, Application API 설명과 예시 | `SPRING_PROFILES_ACTIVE=demo`, springdoc local/demo 노출 설정 |

## 6. 질문을 받았을 때 열 파일

| 질문 | 열 파일 |
| --- | --- |
| 인증/세션은 어떻게 관리하나요? | `src/main/java/com/kinderp/global/security/*`, `src/main/java/com/kinderp/domain/auth/service/*` |
| 권한/tenant 경계는 어디서 막나요? | `src/main/java/com/kinderp/global/security/access/AccessPolicyService.java` |
| 알림 실패는 어떻게 재처리하나요? | `src/main/java/com/kinderp/domain/notification/*`, `src/main/resources/templates/notifications/outbox.html` |
| 입학 workflow는 어디서 관리하나요? | `src/main/java/com/kinderp/domain/kidapplication/service/*` |
| 운영 환경 변수는 어디에 정리했나요? | `docs/guides/env-contract.md` |
| 현재 작업과 검증 계획은 어디에 남기나요? | 루트 `PLAN.md` |

## 7. 시연 실패 시 빠른 복구

### 2분 복구 루트

1. 프로파일 확인: `SPRING_PROFILES_ACTIVE=demo`로 실행했는지 확인합니다.
2. 인프라 확인: `docker compose --env-file docker/.env -f docker/docker-compose.yml ps`로 MySQL/Redis 상태를 봅니다.
3. seed 확인: 원장 로그인 후 `/dashboard`, `/applications/pending`, `/notification-outbox` 순서로 데이터 존재를 확인합니다.
4. 계속 비어 있으면 local/demo DB가 부분 삭제된 상태일 수 있으므로 DB 초기화 후 다시 실행합니다.

- 로그인 실패: `demo` 프로파일인지, seed가 켜졌는지 확인합니다.
- 화면이 비어 있음: `DataLoader`는 seed 계정이 있으면 기본 seed를 다시 만들지 않지만, 시연용 신청/outbox/calendar 샘플은 누락분을 보강합니다. 그래도 비어 있으면 기본 seed 계정/반/원생이 일부 삭제된 DB일 수 있으니 local/demo DB를 초기화합니다.
- Outbox가 비어 있음: `/notification-outbox`는 `APP`, `PUSH`, `EMAIL` dead-letter 샘플을 보강합니다. 샘플이 없으면 principal 계정과 notification/outbox 테이블 상태를 먼저 확인합니다.
- Swagger가 닫힘: `SPRING_PROFILES_ACTIVE=demo`인지 확인합니다.
- Redis/MySQL 연결 실패: `docker compose --env-file docker/.env -f docker/docker-compose.yml ps`로 컨테이너 상태를 확인합니다.

## 8. 최소 검증 루트

시간을 아껴야 할 때는 아래만 확인합니다.

```bash
./gradlew compileJava compileTestJava
git diff --check
```

- API/보안/DB migration을 바꾼 경우에만 관련 targeted test를 추가합니다.
- 전체 `./gradlew test`는 릴리스 직전이나 큰 구조 변경 이후에 실행합니다.
