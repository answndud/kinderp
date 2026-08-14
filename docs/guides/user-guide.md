# Kindergarten ERP 유저 가이드

이 문서는 유치원 ERP를 처음 사용하는 분들을 위한 가이드입니다.
대상은 **원장(PRINCIPAL)**, **선생님(TEACHER)**, **학부모(PARENT)** 입니다.

---

## 1. 이 서비스로 할 수 있는 일

- 회원가입/로그인, 역할 기반 접근
- 유치원/반/원생 관리
- 출결 등록 및 조회
- 알림장 작성/조회/읽음 확인
- 공지사항 작성/조회
- 교사 지원/학부모 입학 신청 승인 워크플로우
- 알림센터(미읽음 배지, 목록, 읽음 처리)
- 일정/캘린더 관리

---

## 2. 시작하기

### 개발 환경 실행 방법 (중요)

- 이 프로젝트는 **터미널에서 Gradle로 실행**하는 것을 권장합니다.
- 권장 실행 명령어:

```bash
./gradlew clean bootRun --args='--spring.profiles.active=local'
```

- 간단 실행(이미 빌드된 상태):

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- 중지: 터미널에서 `Ctrl + C`

#### 왜 터미널 실행을 권장하나요?

- VSCode/IDE Run 버튼은 일부 환경에서 IDE 전용 컴파일 산출물(`bin/main`)로 실행되어,
  Lombok/Annotation Processing 불일치로 런타임 에러가 날 수 있습니다.
- Gradle(`bootRun`)은 프로젝트 표준 빌드 경로(`build/classes`)를 사용하므로 가장 안정적입니다.

#### 실행 전 준비 (최초 1회)

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
```

- MySQL/Redis가 떠 있어야 정상 실행됩니다.
- 종료 시:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down
```

### 공통 진입

1. `/signup` 에서 역할을 선택해 회원가입
2. `/login` 로그인
3. 역할/상태에 따라 자동으로 적절한 페이지로 이동

### 자동 이동 규칙 (중요)

- 원장 + 유치원 미등록: `/kindergarten/create`
- 선생님/학부모 + 승인 대기(PENDING): `/applications/pending`
- 선생님/학부모 + 유치원 미배정: `/applications/pending`
- 그 외: `/`

즉, 가입 후 바로 모든 기능이 열리지 않고, **승인/배정 단계**가 먼저 진행됩니다.

### 로컬 더미데이터 테스트 계정

- 활성 프로필이 `local`이면 DataLoader가 아래 시드 계정을 기준으로 더미데이터를 관리합니다.
- `principal@test.com`, `principal2@test.com` 시드 계정이 이미 있으면 중복 생성을 건너뜁니다.
- 공통 비밀번호: `test1234!`

| 역할 | 아이디(이메일) |
|---|---|
| 원장(PRINCIPAL) | `principal@test.com`, `principal2@test.com` |
| 선생님(TEACHER) | `teacher1@test.com`, `teacher2@test.com`, `teacher3@test.com`, `teacher4@test.com` |
| 학부모(PARENT) | `parent1@test.com`, `parent2@test.com`, `parent3@test.com`, `parent4@test.com`, `parent5@test.com`, `parent6@test.com` |

---

## 3. 역할별 빠른 사용 흐름

## A) 원장 (PRINCIPAL)

### 처음 10분

1. 유치원 등록: `/kindergarten/create`
2. 반 생성/확인: `/classrooms`
3. 지원/신청 승인 확인: `/applications/pending`
4. 원생 관리: `/kids`
5. 운영 화면 확인: `/attendance`, `/notepad`, `/announcements`, `/calendar`

### 할 수 있는 일

- 유치원 등록/수정/삭제
- 반 생성/수정/삭제, 담임 배정/해제
- 원생 등록/수정/삭제, 학부모 연결/해제
- 교사 지원 승인/거절
- 학부모 입학 신청 승인/거절
- 출결 등록/수정/삭제, 특수 상태 처리(결석/지각/조퇴/병결)
- 알림장/공지사항 작성 및 관리
- 일정 생성/수정/삭제
- 대시보드 통계 확인 (`/dashboard`)

---

## B) 선생님 (TEACHER)

### 처음 10분

1. 가입 후 `/applications/pending` 에서 유치원 지원
2. 원장 승인 후 홈(`/`)에서 메뉴 접근
3. 반/원생/출결/알림장 중심으로 사용

### 할 수 있는 일

- 반 생성/수정/조회
- 원생 등록/수정/조회 (삭제/학부모 연결은 원장 권한)
- 출결 등록/수정/조회
- 알림장 작성/수정/삭제
- 공지사항 작성/수정/삭제
- 학부모 입학 신청 승인/거절(소속 유치원 기준)
- 일정(반/개인 범위) 관리

---

## C) 학부모 (PARENT)

### 처음 10분

1. 가입 후 `/applications/pending` 에서 입학 신청
2. 승인 후 자녀 기준 조회 기능 사용
3. 알림장/출결/공지/알림을 확인

### 할 수 있는 일

- 내 입학 신청 등록/조회/취소
- 내 자녀 목록 조회
- 알림장 조회 및 읽음 처리
- 출결 조회(자녀 기준)
- 공지사항 조회
- 알림센터 사용(읽음/삭제)
- 일정 조회 및 개인 일정 관리

---

## 4. 자주 쓰는 화면 경로

- 홈: `/`
- 로그인/회원가입: `/login`, `/signup`
- 프로필/설정: `/profile`, `/settings`
- 지원/승인: `/applications/pending`
- 반 관리: `/classrooms`
- 원생 관리: `/kids`
- 출결: `/attendance`
- 알림장: `/notepad`
- 공지사항: `/announcements`
- 일정/캘린더: `/calendar`
- 알림센터: `/notifications`

---

## 5. 현재 사용 시 알아둘 점

- 대시보드는 원장 전용입니다.
- 식단 기능은 현재 범위에 포함되지 않습니다.
- 출석 월간 리포트는 `/attendance/monthly`에서 원장·교사가 조회할 수 있습니다.
- `/kindergarten/select`는 아직 유치원에 배정되지 않은 교사가 선택 흐름을 시작하는 화면이며, 실제 접근 가능 여부는 역할·승인 상태에 따라 달라집니다.

---

## 6. 문의 전 체크리스트

1. 내 역할/상태가 ACTIVE인지 확인
2. 소속 유치원 배정 여부 확인
3. 접근하려는 메뉴가 내 권한 범위인지 확인
4. 권한이 맞는데도 접근이 안 되면 로그아웃/재로그인 후 재시도
