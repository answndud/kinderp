# Production-Like Checklist

기준일: 2026-08-14

이 문서는 실제 클라우드 배포 없이도 운영 전환 전 확인 가능한 항목을 반복 실행하기 위한 checklist입니다.
실제 운영 배포를 대체하지 않으며, cloud 계정, 운영 도메인, OAuth redirect URI, netcup 내부 MySQL/Redis 접속, backup/rollback은 실제 서버에서 다시 검증해야 합니다.

> 이 문서의 `deploy/docker-compose.prod.yml`과 2026-08-14 실행 결과는 당시 production-like 증거로 보존한다. 현재 실제 실행 SSOT와 Compose는 [`netcup-deployment.md`](./netcup-deployment.md)와 `deploy/docker-compose.netcup.yml`이며, 아래 과거 명령을 실제 netcup 배포 명령으로 간주하지 않는다.

## 1. 목적

- `prod` profile safety guard가 위험 설정을 막는지 확인한다.
- release jar가 만들어지는지 확인한다.
- local/prod compose config가 해석되는지 확인한다.
- 배포 전 미리 볼 수 있는 실패 신호를 문서와 명령으로 고정한다.

## 2. 사전 조건

- Java 21
- Docker Compose
- 저장소 루트에서 실행
- 실제 secret은 사용하지 않는다.
- `deploy/.env.prod.example`은 dry-run 전용 예시 값이다.
- 백업 스크립트 실행 시에는 운영 Redis의 `REDIS_PASSWORD`도 별도로 주입해야 한다.

## 3. 명령

```bash
./gradlew test --tests "com.kinderp.global.config.StartupSafetyValidatorTest"
./gradlew test --tests "com.kinderp.integration.ObservabilityIntegrationTest"
./gradlew test --tests "com.kinderp.integration.ManagementSurfaceOptInIntegrationTest"
./gradlew bootJar
docker build --tag kinderp:quality-check .
docker image inspect kinderp:quality-check --format 'user={{.Config.User}}'
docker compose --env-file docker/.env.example -f docker/docker-compose.yml config >/tmp/docker-compose.base.yml
PROD_ENV_FILE=.env.prod.example docker compose --env-file deploy/.env.prod.example -f deploy/docker-compose.prod.yml config >/tmp/docker-compose.prod.yml
ALERTMANAGER_WEBHOOK_URL=https://hooks.example.com/alerts docker compose --profile alerting -f docker/docker-compose.monitoring.yml config >/tmp/docker-compose.monitoring-alerting.yml
docker run --rm -e APP_DOMAIN=erp.example.com -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2 caddy validate --config /etc/caddy/Caddyfile
bash -n scripts/deploy-with-rollback.sh scripts/backup-production.sh scripts/verify-production-backup.sh scripts/restore-production-backup.sh
PREFLIGHT_ONLY=1 PROD_ENV_FILE=.env.prod.example COMPOSE_ENV_FILE=deploy/.env.prod.example COMPOSE_FILE=deploy/docker-compose.prod.yml ./scripts/deploy-with-rollback.sh
SMOKE_URL=https://erp.example.com/login \
COMPOSE_ENV_FILE=deploy/.env.prod \
COMPOSE_FILE=deploy/docker-compose.prod.yml \
./scripts/deploy-with-rollback.sh
git diff --check
```

## 4. 기대 결과

| 항목 | 기대 결과 |
| --- | --- |
| Startup safety | prod에서 legacy JWT fallback, insecure cookie, seed, Swagger/OpenAPI, app-port Prometheus, wildcard/non-HTTPS CORS가 차단된다. |
| Observability default | 기본 app port에서 Swagger/OpenAPI와 Prometheus가 노출되지 않는다. |
| Management opt-in | local/demo처럼 명시적으로 열었을 때만 Swagger/OpenAPI와 app-port Prometheus가 공개된다. |
| Release package | `bootJar`가 성공한다. |
| Container build | 애플리케이션 이미지가 빌드되고 `user=10001:10001`로 실행된다. |
| Local compose | local Docker compose config가 해석된다. |
| Prod compose dry-run | `PROD_ENV_FILE=.env.prod.example` 주입 시 prod compose config가 해석된다. |
| Secret scope | Redis에는 Redis password만, Caddy에는 domain만 전달된다. |
| Container guardrails | app/Redis/Caddy에 `no-new-privileges`, CPU·메모리 상한, json-file 로그 rotation이 적용된다. |
| Readiness-gated proxy | app 이미지 healthcheck가 management readiness를 확인하고 Caddy는 app `service_healthy` 이후에만 시작한다. |
| Graceful shutdown | prod profile의 `server.shutdown=graceful`과 25초 shutdown phase가 Compose의 30초 stop grace period 안에서 동작한다. |
| Release smoke gate | `SMOKE_URL`이 설정되면 readiness 성공 뒤 HTTPS/HTTP smoke까지 통과해야 배포를 성공으로 판정하고, 실패 시 기존 이미지로 rollback한다. |
| Auth rate-limit contract | login/refresh/signup의 window·limit 기본값과 환경 변수 계약이 문서화되어 있다. |
| Observability signal quality | Caffeine cache statistics가 기록되고 Logback rolling policy가 deprecated API 없이 기동한다. |
| Diff hygiene | `git diff --check`가 통과한다. |

## 5. 2026-08-14 실행 결과

| 명령 | 결과 |
| --- | --- |
| `./gradlew test --tests "com.kinderp.global.config.StartupSafetyValidatorTest"` | 통과 |
| `./gradlew test --tests "com.kinderp.integration.ObservabilityIntegrationTest"` | 통과 |
| `./gradlew test --tests "com.kinderp.integration.ManagementSurfaceOptInIntegrationTest"` | 통과 |
| `./gradlew bootJar` | 통과 |
| `docker compose --env-file docker/.env.example -f docker/docker-compose.yml config >/tmp/docker-compose.base.yml` | 통과 |
| `PROD_ENV_FILE=.env.prod.example docker compose --env-file deploy/.env.prod.example -f deploy/docker-compose.prod.yml config >/tmp/docker-compose.prod.yml` | 통과 |
| `docker build --tag kinderp:quality-check .` | 통과 |
| `docker image inspect kinderp:quality-check --format 'user={{.Config.User}}'` | `user=10001:10001` |
| `docker run --rm -e APP_DOMAIN=erp.example.com -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2 caddy validate --config /etc/caddy/Caddyfile` | 통과 |
| `bash -n scripts/deploy-with-rollback.sh scripts/backup-production.sh scripts/verify-production-backup.sh scripts/restore-production-backup.sh` | 통과 |
| `PREFLIGHT_ONLY=1 PROD_ENV_FILE=.env.prod.example COMPOSE_ENV_FILE=deploy/.env.prod.example COMPOSE_FILE=deploy/docker-compose.prod.yml ./scripts/deploy-with-rollback.sh` | 통과 |
| `./gradlew --no-daemon integrationTest --tests '*ObservabilityIntegrationTest' --tests '*NotificationOutbox*IntegrationTest'` | 32초, 통과 |
| `./gradlew --no-daemon performanceSmokeTest` | 28초, 통과 |
| Docker k6 `k6-auth-notepad-dashboard.js` | 15 VU, 30초, 1,068 requests, error 0.00%, 전체 p95 362.13ms / p99 464.86ms |
| `scripts/backup-production.sh` + `scripts/verify-production-backup.sh` | 로컬 MySQL/Redis backup artifact 생성, staging 정리·atomic promote 및 SHA-256 검증 통과 |
| 임시 MySQL/Redis restore drill | dump/RDB를 별도 disposable container에 복원하고 MySQL `member=12`, `notepad=10`, `notification_outbox=4`, Redis `dbsize=1496` 확인 |
| Docker production-like stack (`kinderp:local-prod`) | MySQL + prod Redis + Spring Boot + Caddy 기동, readiness `200/UP`, 이미지 사용자 `10001:10001` 확인 |
| Docker production-like stack (`kinderp:healthcheck`) | app 컨테이너 `healthy` 확인 후 Caddy가 시작되고 HTTPS `/login` `200` 응답 |
| Caddy HTTPS smoke | `https://localhost/login` `200`, HTTP `/login` `308` HTTPS redirect, `401` unauthenticated API, HSTS/X-Content-Type-Options/X-Frame-Options/Referrer-Policy 확인 |
| Graceful restart recovery | app `docker restart -t 30` 후 readiness `UP`(6번째 polling, 약 10초 이내), HTTPS `/login` `200` |
| Outbox V17 EXPLAIN 비교 | `Using filesort` → `Backward index scan` |
| `git diff --check` | 통과 |

## 6. 운영 전 남은 외부 의존성

- 실제 HTTPS 도메인
- `CORS_ALLOWED_ORIGINS=https://<real-domain>`
- Google/Kakao OAuth redirect URI 운영 도메인 등록
- netcup 내부 MySQL·Redis 접속과 backup 정책
- Redis password/volume/backup 정책
- Caddy TLS 발급 확인
- readiness `UP` 확인
- rollback 대상 image tag와 DB forward-fix 전략
- `scripts/backup-production.sh`로 MySQL/Redis backup artifact 생성
- `scripts/verify-production-backup.sh`로 checksum 검증
- `scripts/restore-production-backup.sh`로 disposable MySQL/Redis 복구 후 `--mysql-assert-*`/`--redis-assert-*` 옵션을 사용한 데이터 assertion 검증
- disposable MySQL/Redis에 복원하는 local restore drill
- 운영 backup은 별도 암호화 object storage로 복제하고 restore drill을 월 1회 수행
- 배포 후 장애는 correlation ID와 `/actuator/health/readiness`를 함께 확인
- DB schema 변경은 rollback 대신 백업 확인 후 forward-fix migration을 우선 검토
- Alertmanager는 `ALERTMANAGER_WEBHOOK_URL`을 주입한 `--profile alerting`으로 기동하고 테스트 alert의 수신을 확인
- HTML 응답은 `Content-Security-Policy` nonce를 포함하고 `Content-Security-Policy-Report-Only`는 사용하지 않으며, 로컬 vendor 자산 외부 CDN/Google Fonts origin은 허용하지 않음. Alpine은 CSP 빌드와 `Alpine.data` 등록 방식을 사용해 `unsafe-eval` 없이 동작한다.
- 템플릿과 HTMX fragment의 액션은 inline event handler가 아니라 `data-action` 이벤트 위임을 사용하고, `script-src-attr 'none'` 정책으로 회귀를 차단한다.

## 7. 로컬 production-like 실행 기록

2026-08-14 Docker Desktop에서 외부 도메인 없이 다음 구성을 실제 기동했다.

```text
base MySQL (docker/docker-compose.yml)
  └─ production Redis + Spring Boot prod image + Caddy (deploy/docker-compose.prod.yml)
```

검증한 것은 과거 production-like 배포 자산과 컨테이너 간 연결·기동·프록시·재시작 복구이며, `localhost`의 Caddy 인증서는 로컬 전용이므로 `curl -k`를 사용했다. 실제 netcup DNS/TLS 발급, 내부 MySQL/Redis 운영 volume, OAuth redirect URI, 외부 webhook provider는 여전히 미실행 상태다.
