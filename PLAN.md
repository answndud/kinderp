# PLAN.md

## Goal

Kindergarten ERP를 TownPet과 중복되지 않는 다중 테넌트 내부 운영 플랫폼 포트폴리오로 전면 개편한다. 권한·승인·출결 정합성, 실패 복구, 성능 증거, 운영형 UI, 실제 배포 증거를 하나의 채용용 서사로 연결하고, 대표 시나리오를 코드·테스트·문서·화면에서 재현 가능하게 만든다.

## Active

### P3 - 배포·최종 포트폴리오 품질

1. 실제 배포와 장애·복구 증거를 확보하고 포트폴리오 산출물을 최종화한다.
   - 파일: `deploy/**`, `scripts/deploy-with-rollback.sh`, `scripts/backup-production.sh`, `scripts/verify-production-backup.sh`, `src/main/java/com/erp/domain/auth/**`, `src/main/java/com/erp/global/exception/GlobalExceptionHandler.java`, `src/main/resources/application-prod.yml`, `Dockerfile`, `.github/workflows/**`, `docs/guides/deployment-guide.md`, `docs/guides/production-like-checklist.md`, `README.md`
   - 변경: 실제 HTTPS 배포, backup/restore, rollback, readiness, 알림 수신, 최소 장애 주입 결과를 기록한다. 로컬 backup artifact와 disposable MySQL/Redis restore drill은 통과했으며, rate-limit 운영값은 환경 계약으로 조정 가능하게 했다. 템플릿 액션은 CSP 호환 `data-action` 이벤트 위임으로 전환했고 `script-src-attr 'none'` 회귀 검사를 CI에 연결했다. 대시보드 통계 로직은 `static/js/pages/dashboard.js`로 분리하고 차트 색상·진행률을 CSS 토큰과 접근성 라벨로 연결했다. 원생 목록·출결·신청 화면에는 로딩 상태, 갱신 상태, 모바일 상태 전달을 보강하고 출결 기준일을 Asia/Seoul로 고정했다. 알림장·공지·일정 목록에도 live region과 busy 상태를 적용하고 공지 필터는 `aria-pressed`로 상태를 전달하며 일정 범위를 Asia/Seoul 기준으로 통일했다. 인증/업무 감사 로그는 표 caption·오류 alert·갱신 live region을 보강했고, 설정의 활성 세션 목록은 동적 busy 상태와 오류 alert를 제공하며 비밀번호 autocomplete을 명시했다. 로그인·회원가입은 autocomplete 및 오류 alert를 보강하고 전용 Alpine 로직을 `static/js/pages/auth-forms.js`로 분리했으며, 출결 요청·월간 리포트는 모바일 목록/표와 동적 busy 상태를 명시하며 월 초기화 기준을 Asia/Seoul로 통일했다. 월간 리포트 조회/렌더링은 `static/js/pages/monthly-report.js`로 분리하고 표·모바일 카드의 사용자 입력 escaping을 적용했다. 출결 요청 큐는 `static/js/pages/attendance-requests.js`로 분리하고 승인/반려/취소 중복 클릭 방지와 목록 busy 상태를 보강했다. 인증·업무 감사 로그는 공통 `static/js/pages/audit-log-page.js`로 통합하고 유형별 API/행 렌더링을 `data-audit-kind`로 분기했다. 유치원 선택·등록과 반 관리에는 목록 busy/빈/오류 상태, 조직·주소·전화 자동완성, 반 생성 중복 제출 방지를 적용했으며 등록 제출 로직은 `static/js/pages/kindergarten-create.js`, 유치원 선택은 `static/js/pages/kindergarten-select.js`, 반 관리 조회/생성은 `static/js/pages/classrooms.js`로 분리했다. 프로필 수정·공지 미리보기·알림장 대상 선택 로직은 각각 `static/js/pages/profile.js`, `static/js/pages/announcement-editor.js`, `static/js/pages/notepad-write.js`로 분리하고 서버 주입 값은 `data-*` 설정으로 전달했다. 원생 등록/수정·상세 학부모 관리와 공지/알림장 편집의 화면 로직도 전용 모듈로 분리했다. 외부 도메인·운영 자격증명이 필요한 항목은 미실행으로 구분한다.
   - 검증: `./gradlew bootJar`; `docker compose ... config`; 배포 URL smoke; backup/restore checksum 비교
   - 완료: 외부에서 접근 가능한 데모와 배포 SHA, 복구 결과, 운영 runbook, 최종 README가 일치한다.
## Backlog

- 실제 provider sandbox 연동은 자격 증명 확보 후 Outbox adapter 검증 범위에서 진행한다.
- 운영 DB/Redis restore drill과 Alertmanager 수신 채널 연결은 인프라 접근 권한이 생기면 실행한다.
- CDN을 제거하지 못하는 외부 자산만 공식 digest 확인 후 SRI를 적용한다.
- 원생 관리 화면은 `static/js/pages/kids.js`, 출결 화면은 `static/js/pages/attendance.js`, 신청/승인 화면은 `static/js/pages/applications.js`로 분리했다. 대시보드 통계는 `static/js/pages/dashboard.js`로 분리하고 도넛 차트 CSS 토큰·접근성 라벨을 적용했다. 원생 목록에는 `aria-busy`/`aria-live`, 출결 상태에는 원자적 live region, 신청 fragment에는 갱신 live region을 적용했다. 알림장·공지·일정 fragment에도 목록 live region/busy 상태를 적용하고, 상세 화면의 반복 인라인 스타일은 공통 CSS로 이동했다. 인증/업무 감사 로그와 계정 설정의 동적 영역에도 표 caption, alert, live region, 세션 busy 상태를 적용했다. 감사 로그는 `static/js/pages/audit-log-page.js`, 출결 요청 큐는 `static/js/pages/attendance-requests.js`로 공통 모듈화하고 필터 URL 보존, 승인 중복 클릭 방지, 오류/빈 상태를 통합했다. 로그인·회원가입 폼에 계정 자동완성과 오류 alert를 적용하고 인증 Alpine 로직은 `static/js/pages/auth-forms.js`로 분리했으며 출결 요청·월간 리포트의 모바일 목록/표와 busy 상태를 보강했다. 월간 리포트 조회/렌더링은 `static/js/pages/monthly-report.js`로 분리했고 원생 등록/수정·상세 학부모 관리 및 공지/알림장 편집도 전용 모듈로 이동했다. 유치원 선택/등록·반 관리에도 목록 상태 전달, 입력 autocomplete, 생성 중복 제출 방지를 적용했고 유치원 등록 제출은 `static/js/pages/kindergarten-create.js`, 유치원 선택은 `static/js/pages/kindergarten-select.js`, 반 관리는 `static/js/pages/classrooms.js`로 분리했다. 알림 런타임과 header controller도 `static/js/notifications.js`, `static/js/header.js`로 분리했으며, 화면별 서버 주입 값은 `data-*` 설정으로 전달한다.
