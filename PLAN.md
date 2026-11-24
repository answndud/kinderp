# PLAN.md

No active work

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
- 출결 외 도메인도 컨트롤러가 엔티티를 받아 트랜잭션 밖에서 DTO로 변환한다 (OSIV OFF 원칙 위반 소지). 서비스가 DTO를 반환하도록 바꿀 대상: `AnnouncementController`(4곳), `ClassroomController`(5곳), `KindergartenController`(3곳), `NotepadController`(2곳), `KidController`(3곳), `MemberApiController`·`AuthApiController`(`MemberResponse.from`).
