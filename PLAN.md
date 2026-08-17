# PLAN.md

## Goal

KinderP를 TownPet과 차별화되는 다중 테넌트 내부 운영 플랫폼 포트폴리오로 완성한다. 코드·화면·테스트·운영 문서가 대표 업무 흐름과 일치하고, 실제 배포 전 남은 외부 의존 항목이 명확히 검증 가능한 상태가 되는 것이 완료 조건이다.

## Active

### P2 - netcup 단일 VPS 공동 배포 기반

> 상태: Compose·배포 스크립트·환경 정책·Resend SMTP fail-closed 설정·암호화 백업 자산, backend compile/bootJar와 frontend asset/accessibility 검증, local MySQL·Redis·Spring Boot health/root 기동을 완료했다. netcup 승인 후 실제 MySQL/Redis/app readiness·메일 발송과 복구 리허설을 수행한다.

> 검증 메모(2026-08-16): Docker Desktop을 실행한 뒤 전체 `./gradlew test`가 2분 56초 만에 통과했다. Testcontainers 기반 통합 테스트까지 포함한 결과이며, 실제 netcup 외부 의존 검증은 별도 항목으로 남아 있다.

1. netcup x86 VPS Lite 2용 Compose를 준비한다.
   - 파일: `deploy/docker-compose.netcup.yml`, `deploy/Caddyfile.netcup`, `deploy/.env.netcup.example`, `scripts/validate-netcup-env.sh`
   - 변경: MySQL을 VPS 내부 service로 추가하고, ERP Caddy의 80/443 공개를 제거해 공용 edge Caddy에 연결한다. app·MySQL·Redis·Caddy의 메모리/로그 제한을 유지하고 Resend SMTP를 production profile에 연결한다.
   - 검증: `docker compose config`와 외부 포트 정적 검사를 완료했다. MySQL·Redis·app readiness는 실제 Docker/VPS에서 남아 있다.
   - 완료: TownPet stack과 같은 `edge` network에서 ERP가 `erp.example.com` upstream으로 응답한다.

2. ERP MySQL backup/restore를 TownPet 배포 계획과 연결한다.
   - 파일: `deploy/backup-mysql.sh`, `deploy/restore-mysql.sh`, `docs/guides/deployment-guide.md`
   - 변경: MySQL dump·checksum·destructive restore guard와 netcup 실행 명령을 추가한다.
   - 검증: dump·checksum·destructive restore guard와 age 암호화 자산을 확인했다. disposable MySQL 복구와 핵심 table count는 Docker 실행 환경에서 남아 있다.
   - 완료: ERP DB를 외부 암호화 backup 대상으로 포함하고 복구 명령을 재현할 수 있다.

### P3 - 외부 운영 증거 확보

1. 실제 HTTPS 환경에서 배포·롤백·복구 증거를 확보한다.
   - 파일: `deploy/**`, `scripts/deploy-with-rollback.sh`, `scripts/backup-production.sh`, `scripts/verify-production-backup.sh`, `scripts/restore-production-backup.sh`, `.github/workflows/cd.yml`, `docs/guides/deployment-guide.md`, `docs/guides/production-like-checklist.md`
   - 변경: 실제 도메인·TLS·운영 DB/Redis 자격증명을 연결하고, 이미지 SHA·readiness·smoke·rollback·backup/restore 결과를 기록한다. 운영 schema 변경은 rollback 대신 forward-fix 정책을 확인한다.
   - 검증: 로컬 production-like stack의 readiness·SHA·rollback 자산과 disposable MySQL/Redis restore drill을 확인했고, 복구 스크립트에 데이터 assertion 옵션을 추가했다. 남은 검증은 `PREFLIGHT_ONLY=1 ... ./scripts/deploy-with-rollback.sh`의 실제 환경 실행, 실제 배포 URL smoke, `/actuator/info` SHA 대조, 운영 backup/restore 및 rollback 결과다.
   - 완료: 외부 URL에서 새 이미지의 readiness와 SHA가 확인되고, 실패 주입 후 이전 이미지·이전 `APP_VERSION`으로 복구되며, 복구 데이터 assertion이 남는다.
   - 주의: cloud 계정, DNS, TLS, RDS/Redis, OAuth redirect URI가 필요하다. 자격증명 없이 완료로 표시하지 않는다.

2. 외부 알림 provider sandbox와 incident 수신 채널을 연결한다.
   - 파일: `src/main/java/com/erp/domain/notification/service/channel/**`, `src/main/resources/application-prod.yml`, `docs/guides/risk-response.md`, `docs/guides/deployment-guide.md`
   - 변경: provider webhook signature, provider rate limit, delivery/retry/dead-letter, incident 알림 수신을 실제 sandbox에서 확인한다.
   - 검증: 로컬 통합 테스트에서 실제 payload 기준 HMAC 서명 계약과 dead-letter 전이를 확인했다. 남은 검증은 provider sandbox smoke, provider 서명 검증 실패, dead-letter 재시도 후 외부 수신, Alertmanager test alert 수신이다.
   - 완료: 외부 provider 성공·실패·재시도와 incident 채널 수신 결과가 correlation ID와 함께 기록된다.
   - 주의: provider 계정·webhook secret·Alertmanager 수신 URL이 필요하다.

## Backlog

- 실제 운영 트래픽 규모에서 k6 시나리오와 MySQL 실행계획을 재측정한다.
- CDN 또는 외부 자산을 추가할 경우 공식 digest/SRI 검토를 거친다.
