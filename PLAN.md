# PLAN.md

## Goal

Kindergarten ERP를 TownPet과 차별화되는 다중 테넌트 내부 운영 플랫폼 포트폴리오로 완성한다. 코드·화면·테스트·운영 문서가 대표 업무 흐름과 일치하고, 실제 배포 전 남은 외부 의존 항목이 명확히 검증 가능한 상태가 되는 것이 완료 조건이다.

## Active

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
