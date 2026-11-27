# TRACK.md

No active work

## Backlog (이번 계획 범위 밖)

- 기존 netcup 배포 외부 증거 확보(SMTP 실수신, 암호화 백업 외부 보관, 이미지 롤백, provider sandbox). 상세 내용은 `git show 21ec2e4:PLAN.md`(파일 이름을 바꾸기 전 이력)의 P2/P3를 참고한다.
- `/kids`와 `/kids/page`는 응답 모양(목록·페이지)과 사용처(드롭다운·관리 표)가 달라 URL을 유지했다. 컨트롤러의 중복 분기는 합쳤지만, `KidService`의 List·Page 오버로드 8개는 `AttendanceService`가 List 버전을 직접 써서 남아 있다. 하나로 줄이려면 호출부를 먼저 정리해야 한다.
- 멱등 키로 재전송한 출결 변경 요청도 원래 응답과 같은 201을 돌려준다. 재전송을 200으로 구분하려면 서비스가 "새로 만들었는지"를 반환해야 한다.
- `ProductTime` static을 `Clock` 주입으로 전환 (시간 의존 테스트 용이성).
- 공개 데모 데이터 주기적 초기화(예: 매일 새벽 시드 재적재). 방문자가 데모 데이터를 훼손할 수 있음.
- CD workflow(`cd.yml`, `cd-netcup.yml`, `container-publish.yml`)와 `deploy/` compose·Caddy 설정을 실제 사용하는 netcup 경로 하나로 통합하고, 남는 workflow의 action을 SHA로 고정.
- 출결 외 도메인도 컨트롤러가 엔티티를 받아 트랜잭션 밖에서 DTO로 변환한다 (OSIV OFF 원칙 위반 소지). 대상: `AnnouncementController`(4곳), `ClassroomController`(5곳), `KindergartenController`(3곳), `NotepadController`(2곳), `KidController`(3곳), `MemberApiController`·`AuthApiController`(`MemberResponse.from`). 서비스 반환 타입을 약 35곳 바꿔야 하고 `getKid`·`getClassroom` 같은 엔티티 반환 메서드를 다른 서비스가 내부에서 널리 쓰므로, 현재 통합 테스트가 모두 통과하는 동안은 보류하고 실제 lazy 초기화 실패가 관측될 때 도메인 단위로 진행한다.
- 로그인 rate limit의 점진적 지연(백오프) 또는 CAPTCHA. 지금은 IP+이메일 쌍 한도와 이메일 전체 backstop까지만 적용했고, 분산 공격이 전체 한도(50회/10분)를 채우면 여전히 정상 사용자가 막힌다.
