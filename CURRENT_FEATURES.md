# 현재 테스트 가능 기능

> 로컬 실행 기준(2026-03-28)으로 실제 동작하는 기능만 정리한 문서입니다.

---

## 1) 실행 방법

### 사전 요구사항
- Java 21
- Docker Desktop

### 인프라 실행
```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
```

### 애플리케이션 실행
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

- 시드 데이터가 필요하면 `APP_SEED_ENABLED=true`를 함께 넘깁니다.
- Swagger/OpenAPI와 app-port Prometheus는 `local`/`demo`에서만 의도적으로 공개합니다.

### 접속
- 앱: `http://localhost:8080`
- 로그인: `http://localhost:8080/login`
- 회원가입: `http://localhost:8080/signup`

---

## 2) 인증/권한

### 로컬 로그인 (이메일/비밀번호)
- 회원가입 후 `/api/v1/auth/login`으로 로그인
- JWT(access/refresh)는 HTTP-only 쿠키로 발급

### 소셜 로그인 (OAuth2)
- 로그인 화면 버튼으로 진입
  - Google: `/oauth2/authorization/google`
  - Kakao: `/oauth2/authorization/kakao`
- 같은 이메일 기존 계정이 있으면 자동 연결하지 않고 충돌 안내 후 기존 로그인 방식을 유도
- 설정 화면에서 명시적으로 소셜 계정을 연결/해제
- 동일 provider의 다른 계정으로 교체는 허용하지 않음

### 역할
- `PRINCIPAL`, `TEACHER`, `PARENT`
- 주요 API는 `@PreAuthorize` 기반으로 역할 제한

---

## 3) 도메인 기능

### 유치원/반/원생
- 유치원 CRUD (`/api/v1/kindergartens`)
- 반 CRUD + 담임 배정/해제 (`/api/v1/classrooms`)
- 원생 CRUD + 반 이동 + 학부모 연결 (`/api/v1/kids`)

### 출석
- 출석 upsert, 일괄 반영 (`/api/v1/attendance/upsert`, `/api/v1/attendance/bulk`)
- 일별/월별 조회, 반별 월간 리포트
- 등원/하원/결석/지각/조퇴/병결 처리
- 학부모 출결 변경 요청 생성/취소 (`/api/v1/attendance-requests`)
- 교사/원장 출결 변경 요청 승인/거절 및 상세 조회

### 알림장
- 반/원생/전체 알림장 작성/수정/삭제 (`/api/v1/notepads`)
- 학부모 읽음 처리 (`/api/v1/notepads/{id}/read`)

### 공지사항
- 공지 CRUD, 중요 공지 토글 (`/api/v1/announcements`)
- 중요 공지/검색/인기 공지 조회

### 지원/승인
- 교사 유치원 지원 (`/api/v1/kindergarten-applications`)
- 학부모 원생 입학 신청 (`/api/v1/kid-applications`)
- 교사 지원 승인/거절/취소 워크플로우
- 입학 waitlist / offer / offer 수락 / offer 만료 워크플로우

### 알림
- 알림 목록/상세/미읽음 개수/읽음/삭제 (`/api/v1/notifications`)

### 캘린더
- 일정 CRUD + today/upcoming 조회 (`/api/v1/calendar/events`)

### 대시보드
- 통계 API: `/api/v1/dashboard/statistics`
- 대시보드 화면: `/dashboard`
- 입소일/주말을 반영한 출석률, 고유 열람 기준 공지 열람률

---

## 4) 빠른 API 검증 예시

아래 예시는 CSRF 토큰 처리 없이 개념 확인용입니다. 실제 브라우저/테스트에서는 CSRF가 적용됩니다.

```bash
# 회원가입
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email":"parent@example.com",
    "password":"Test1234!",
    "passwordConfirm":"Test1234!",
    "name":"테스트학부모",
    "phone":"01012345678",
    "role":"PARENT"
  }'

# 로그인 (쿠키 저장)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"parent@example.com","password":"Test1234!"}' \
  -c cookies.txt

# 내 정보 조회
curl -X GET http://localhost:8080/api/v1/auth/me -b cookies.txt
```

---

## 5) DB/Redis 확인 포인트

### 주요 테이블
- 기본: `member`, `kindergarten`, `classroom`, `kid`, `attendance`, `notepad`, `announcement`
- 워크플로우: `kindergarten_application`, `kid_application`, `attendance_change_request`, `notification`, `notification_outbox`, `calendar_event`
- 운영/감사: `auth_audit_log`, `auth_audit_log_archive`, `domain_audit_log`, `member_social_account`

### Redis
- refresh token key: `refresh:session:{memberId}:{sessionId}`
- 세션 목록 set: `refresh:sessions:{memberId}`
- 세션 메타데이터: `refresh:session:meta:{memberId}:{sessionId}`
- 인증 rate limit / anomaly / 세션 검증도 Redis를 사용

---

## 6) 참고 문서

- 전체 개요: `README.md`
- 문서 인덱스: `docs/README.md`
- active 계획: `PLAN.md`
- active 진행 상태: `PLAN.md`의 `Active` 섹션
- 완료 archive: `docs/COMPLETED.md`
- 개발자 가이드: `docs/guides/developer-guide.md`
- 환경 변수 계약: `docs/guides/env-contract.md`
- 배포 가이드: `docs/guides/deployment-guide.md`
