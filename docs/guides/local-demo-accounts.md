# 로컬 데모 계정과 테스트 시나리오

이 문서는 KinderP를 로컬에서 실행한 뒤 브라우저로 로그인하고, 역할별 기능을 빠르게 확인하기 위한 안내서입니다.

아래 계정과 비밀번호는 합성 시드 데이터 전용입니다. 실제 개인정보는 포함하지 않으며, 공개 포트폴리오 showcase에서는 방문자가 역할별 화면을 확인할 수 있도록 사용할 수 있습니다. 상용 운영이나 실제 사용자 데이터가 있는 환경에서는 재사용하지 않습니다.

## 1. 실행 프로파일

### 공개 포트폴리오 배포

netcup showcase에서는 `SPRING_PROFILES_ACTIVE=prod`, `APP_PUBLIC_DEMO_ENABLED=true`, `APP_SEED_ENABLED=true`를 명시합니다. `StartupSafetyValidator`가 이 조합을 공개 demo 예외로 허용하며 Swagger·Prometheus는 계속 비공개입니다.

### 면접·시연용 권장 실행

`demo` 프로파일은 `local` 설정을 포함하고 시드 데이터가 자동으로 활성화됩니다.

```bash
cd /Users/alex/project/kinderp/erp
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

브라우저 접속 주소:

- 애플리케이션: [http://localhost:8080](http://localhost:8080)
- 로그인: [http://localhost:8080/login](http://localhost:8080/login)
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### `local` 프로파일 실행

`local`은 기본적으로 시드가 꺼져 있으므로 계정과 샘플 데이터가 필요하면 `APP_SEED_ENABLED=true`를 함께 지정합니다.

```bash
cd /Users/alex/project/kinderp/erp
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
APP_SEED_ENABLED=true SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`local`에서 Swagger까지 열어야 한다면 `demo` 프로파일을 사용하는 편이 간단합니다. 시드 로그에 비밀번호를 출력할 필요는 없으며, 이 문서의 고정 데모 계정을 사용하면 됩니다.

## 2. 공통 로그인 정보

모든 시드 계정의 비밀번호는 동일합니다.

| 항목 | 값 |
| --- | --- |
| 비밀번호 | `test1234!` |
| 로그인 경로 | `/login` |
| 인증 방식 | 이메일 + 비밀번호, JWT HTTP-only cookie |
| 시간 기준 | `Asia/Seoul` |

### 원장 계정

| 이메일 | 이름 | 소속 유치원 | 역할 | 우선 확인 화면 |
| --- | --- | --- | --- | --- |
| `principal@test.com` | 김원장 | 해바라기 유치원 | `PRINCIPAL` | `/dashboard`, `/applications/pending`, `/notification-outbox` |
| `principal2@test.com` | 이원장 | 꿈나무 유치원 | `PRINCIPAL` | `/dashboard`, `/classrooms`, `/kids` |

### 교사 계정

| 이메일 | 이름 | 소속 유치원 | 담당 반 | 우선 확인 화면 |
| --- | --- | --- | --- | --- |
| `teacher1@test.com` | 김교사 | 해바라기 유치원 | 해바라기반 | `/attendance`, `/attendance-requests`, `/calendar` |
| `teacher2@test.com` | 박교사 | 해바라기 유치원 | 장미반 | `/attendance`, `/notepad`, `/announcements` |
| `teacher3@test.com` | 최교사 | 꿈나무 유치원 | 나무반 | `/attendance`, `/calendar` |
| `teacher4@test.com` | 정교사 | 꿈나무 유치원 | 꽃반 | `/attendance`, `/notepad` |

### 학부모 계정

| 이메일 | 이름 | 소속 유치원 | 연결 원아 | 우선 확인 화면 |
| --- | --- | --- | --- | --- |
| `parent1@test.com` | 준우아빠 | 해바라기 유치원 | 준우, 시우 | `/notepad`, `/attendance`, `/notifications` |
| `parent2@test.com` | 서윤엄마 | 해바라기 유치원 | 서윤, 하은 | `/notepad`, `/applications/pending` |
| `parent3@test.com` | 시우할아빠 | 해바라기 유치원 | 도윤, 지호 | `/notepad`, `/calendar` |
| `parent4@test.com` | 주원엄마 | 꿈나무 유치원 | 주원, 다은 | `/notepad`, `/applications/pending` |
| `parent5@test.com` | 수빈아빠 | 꿈나무 유치원 | 수빈, 예준 | `/attendance`, `/notifications` |
| `parent6@test.com` | 지원할머니 | 꿈나무 유치원 | 지원, 연우 | `/notepad`, `/calendar` |

## 3. 시드 데이터 구성

처음 시드가 생성되면 다음과 같은 기본 데이터가 만들어집니다.

| 데이터 | 수량 | 주요 샘플 |
| --- | ---: | --- |
| 유치원 | 2개 | 해바라기 유치원, 꿈나무 유치원 |
| 원장 | 2명 | 유치원별 1명 |
| 교사 | 4명 | 유치원별 2명 |
| 반 | 4개 | 해바라기반, 장미반, 나무반, 꽃반 |
| 원아 | 12명 | 유치원별 6명 |
| 학부모 | 6명 | 유치원별 3명 |
| 출결 | 최근 7일 | 원아별 출결 샘플 |
| 입학 신청 | 4건 | `PENDING`, `WAITLISTED`, `OFFERED`, `APPROVED` |
| Outbox | 4건 | `APP`, `PUSH`, `EMAIL` 실패 샘플과 전달 성공 샘플 |
| 일정 | 3건 | 입학 상담, 반복 미술 활동, 운영 지표 점검 |
| 인증 감사 로그 | 복수 | 로그인, refresh, social link/unlink 성공·실패 |
| 업무 감사 로그 | 복수 | 입학 대기열·offer·승인 이력 |

시드는 이미 원장 계정이 있으면 계정을 중복 생성하지 않습니다. 이 경우 시연용 입학 신청, Outbox, 캘린더, 감사 로그 샘플은 누락된 항목만 보강합니다.

## 4. 역할별 테스트 순서

### 원장: 전체 운영 콘솔 확인

추천 계정: `principal@test.com / test1234!`

1. `/login`에서 로그인합니다.
2. `/dashboard`에서 원생 수, 교사 수, 학부모 수, 오늘 출결 요약을 확인합니다.
3. `/classrooms`에서 해바라기반·장미반과 담임 배정을 확인합니다.
4. `/kids`에서 원아 목록과 학부모 연결을 확인합니다.
5. `/applications/pending`에서 `PENDING`, `WAITLISTED`, `OFFERED` 신청을 확인합니다.
6. `/notification-outbox`에서 `status`, `channel`, 검색어 필터를 사용하고 dead-letter retry 버튼을 확인합니다.
7. `/audit-logs`에서 인증 이벤트와 reason 필터를 확인합니다.
8. `/domain-audit-logs`에서 입학 workflow 업무 변경 이력을 확인합니다.
9. `/calendar`에서 유치원 전체 일정과 개인 운영 점검 일정을 확인합니다.

원장 계정으로 가장 많은 운영 기능과 다중 테넌트 경계를 한 번에 보여줄 수 있습니다.

### 교사: 반·출결·알림장 확인

추천 계정: `teacher1@test.com / test1234!`

1. `/attendance`에서 담당 반 원아의 오늘 출결을 확인합니다.
2. 출결 상태를 출석·결석·지각·조퇴·병결 중 하나로 변경합니다.
3. `/attendance-requests`에서 학부모 출결 변경 요청 큐를 확인합니다.
4. `/attendance/monthly`에서 월간 출결 리포트를 확인합니다.
5. `/notepad`에서 담당 반 알림장을 작성하거나 조회합니다.
6. `/announcements`에서 공지사항을 확인합니다.
7. `/calendar`에서 반 반복 일정과 개인 일정을 확인합니다.

교사 계정은 소속 유치원과 담당 반 범위가 적용되므로 다른 유치원의 데이터가 노출되지 않는지도 함께 확인할 수 있습니다.

### 학부모: 자녀 중심 조회와 요청 확인

추천 계정: `parent1@test.com / test1234!`

1. `/notepad`에서 연결된 자녀 준우·시우의 알림장을 확인합니다.
2. 알림장 읽음 처리를 실행합니다.
3. `/attendance`에서 자녀 출결을 확인합니다.
4. `/applications/pending`에서 본인의 입학 신청만 확인합니다.
5. `/notifications`에서 알림 읽음·삭제를 확인합니다.
6. `/calendar`에서 자녀 또는 소속 유치원과 관련된 일정을 확인합니다.
7. 출결 변경 요청을 생성해 교사 계정의 `/attendance-requests`에서 보이는지 확인합니다.

학부모는 본인과 연결된 원아만 조회할 수 있습니다. 다른 학부모의 자녀나 다른 유치원의 원아를 URL ID를 바꿔 조회할 수 없어야 합니다.

## 5. 5분 빠른 시연 루트

시간이 부족하면 아래 순서만 확인합니다.

1. `principal@test.com`으로 로그인
2. `/dashboard`에서 운영 지표 확인
3. `/applications/pending`에서 입학 상태 4종 확인
4. `/notification-outbox`에서 실패 채널과 retry 흐름 확인
5. `/audit-logs` 또는 `/domain-audit-logs`에서 운영 추적성 확인
6. 로그아웃 후 `parent1@test.com`으로 로그인
7. `/notepad`와 `/attendance`에서 자녀 기준 데이터 범위 확인

## 6. 상태·권한에 따른 자동 이동

시드 계정은 이미 유치원과 연결되어 있으므로 로그인 직후 역할별 홈으로 이동합니다.

일반 가입 계정은 다음 상태에 따라 자동 이동할 수 있습니다.

| 상태 | 자동 이동 |
| --- | --- |
| 원장이고 유치원 미등록 | `/kindergarten/create` |
| 교사·학부모이고 승인 대기 또는 미배정 | `/applications/pending` |
| 정상적으로 유치원에 소속됨 | 역할별 기본 홈 |

따라서 신규 가입·승인 workflow를 테스트하려면 시드 계정과 별도의 새 이메일을 사용하세요.

## 7. 초기화와 재실행

일반적으로 컨테이너를 재시작해도 DB volume은 보존되므로 계정과 샘플 데이터가 유지됩니다.

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
```

로컬 데이터를 모두 지우고 처음부터 다시 만들 때만 volume 삭제를 사용합니다.

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down -v
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

`down -v`는 로컬 MySQL·Redis volume의 데이터를 삭제하므로 개인 테스트 데이터가 필요하면 실행하지 마세요. 운영 데이터나 외부 데이터베이스에는 이 명령을 사용하지 않습니다.

## 8. 로그인 실패·화면이 비어 있을 때

### 로그인 실패

- `SPRING_PROFILES_ACTIVE=demo`로 실행했는지 확인합니다.
- `local` 프로파일이라면 `APP_SEED_ENABLED=true`를 지정했는지 확인합니다.
- 이메일 철자와 비밀번호 `test1234!`를 확인합니다.
- 서버를 재시작한 직후라면 Flyway migration과 DataLoader 로그가 끝났는지 확인합니다.

### 화면에 데이터가 없음

- 원장으로 먼저 `/dashboard`에 로그인해 기본 seed가 생성됐는지 확인합니다.
- `/applications/pending`에 신청 샘플이 없으면 서버 로그에서 demo scenario supplement 실행 여부를 확인합니다.
- `/notification-outbox`가 비어 있으면 원장 계정과 `notification_outbox` 샘플 생성 여부를 확인합니다.
- DB를 초기화했다면 서버가 migration을 끝낸 뒤 demo 프로파일로 한 번 실행해야 합니다.

### 인프라 연결 실패

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml ps
docker compose --env-file docker/.env -f docker/docker-compose.yml logs mysql redis
```

MySQL과 Redis가 실행 중인지 확인한 뒤 Spring Boot를 다시 시작합니다.

## 9. 구현 근거

- 시드 계정과 데이터 생성: `src/main/java/com/erp/global/config/DataLoader.java`
- demo 프로파일: `src/main/resources/application-demo.yml`
- 환경 변수 계약: `docs/guides/env-contract.md`
- 전체 데모 시연 순서: `docs/guides/demo-scenario.md`
- 역할별 일반 사용법: `docs/guides/user-guide.md`
