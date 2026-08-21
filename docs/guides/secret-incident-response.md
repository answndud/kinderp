# Secret 유출·DB 침해 대응 Runbook

기준일: 2026-08-21

이 문서는 JWT/OAuth/DB/Redis/SMTP/webhook/GHCR/백업 자격증명 유출 또는 DB 침해가 의심될 때의 공통 대응 순서다. 실제 secret 값은 이 문서나 셸 명령에 입력하지 않고 secret manager·운영 secret 파일에서만 읽는다.

## 1. 자격증명 inventory와 책임

| 자격증명 | 사용처 | 1차 책임자 | 즉시 조치 | 검증 |
| --- | --- | --- | --- | --- |
| `JWT_SECRET` | access/refresh JWT 서명 | 애플리케이션 운영자 | 새 값 발급 후 app 재기동 | 이전 JWT가 401, 새 로그인 성공 |
| `DB_PASSWORD`, `FLYWAY_DB_PASSWORD` | MySQL 앱/Flyway 계정 | DB 운영자 | 두 계정 비밀번호를 각각 교체하고 배포 secret 갱신 | 이전 비밀번호 접속 실패, migration/앱 readiness 성공 |
| `REDIS_PASSWORD` | Redis 세션/rate limit | 인프라 운영자 | Redis 비밀번호 교체 및 app/backup secret 동시 갱신 | 이전 비밀번호 PING 실패, 새 연결 성공 |
| Google/Kakao client secret | OAuth provider | 인증 운영자 | provider console에서 revoke·재발급, redirect allowlist 재확인 | 이전 secret OAuth 실패, 새 callback 성공 |
| SMTP/API webhook secret | Resend·push/app/incident sender | 알림 운영자 | provider key/webhook secret revoke·재발급 | 이전 서명/키 실패, sandbox delivery 성공 |
| GHCR read token | 배포 서버 image pull | 배포 운영자 | fine-grained token revoke·최소 scope로 재발급 | 이전 token pull 실패, 새 token pull 성공 |
| `AGE_RECIPIENT` 대응 private key | 백업 복호화 | 백업 운영자 | recipient key revoke/교체, 새 백업으로 복호화 확인 | 이전 키 접근 실패, 새 키 restore 성공 |
| backup SSH key | 외부 백업 저장소 전송 | 인프라 운영자 | 원격 `authorized_keys` 제거·새 key 등록 | 이전 key SSH 실패, 새 key 전송 성공 |

## 2. 순서

1. **Contain**: 공개 ingress 또는 배포를 일시 차단하고 correlation ID, access log, CI/deploy log, DB audit log를 보존한다. 의심 파일과 로그를 삭제하지 않는다.
2. **Revoke**: 노출된 provider key·OAuth secret·GHCR token·SSH key를 provider/원격 저장소에서 먼저 revoke한다.
3. **Invalidate sessions**: `JWT_SECRET`을 교체하고 앱을 재기동한다. 기존 Redis refresh session은 아래 명령으로 명시적 전체 revoke한다.
4. **Rotate infrastructure**: DB 앱 계정과 Flyway 계정, Redis, SMTP/webhook, backup key를 각각 새 값으로 발급하고 `/opt/kinderp/secrets` 파일을 `chmod 600`으로 교체한다.
5. **Recover and verify**: disposable restore와 smoke를 수행하고, 이전 자격증명 실패·새 자격증명 성공·새 JWT 발급을 기록한다.
6. **Notify and learn**: 영향 범위·노출 시간·삭제/보존 근거·담당자·재발 방지 변경을 incident 기록에 남긴다.

## 3. 세션 전체 무효화

JWT secret 교체만으로 access token은 무효화되지만 Redis refresh session도 즉시 폐기해야 한다.

```bash
set -a
. /opt/kinderp/secrets/backup.env
set +a
REDIS_CONTAINER=kinderp-redis \
REDIS_PASSWORD="$REDIS_PASSWORD" \
./scripts/revoke-production-sessions.sh --confirm-all-sessions
```

이 스크립트는 `refresh:*` 키만 대상으로 하며, 확인 인자가 없으면 실행하지 않는다. 실행 전 현재 활성 세션 수와 incident 승인자를 기록한다.

## 4. 검증 증적

- 이전 JWT로 보호 API 호출 시 `401`
- 이전 DB/Redis/SMTP/webhook/GHCR/SSH credential 사용 시 실패
- 새 credential로 앱 readiness, Flyway, Redis PING, 알림 sandbox, GHCR pull 성공
- 새 age key로 backup decrypt와 disposable restore 성공
- 로그·CI·provider audit에서 revoke 시각과 correlation ID 확인
