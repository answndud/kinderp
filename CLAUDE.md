# CLAUDE.md

이 저장소의 에이전트 작업 기준은 루트 [`AGENTS.md`](AGENTS.md)와 [`PLAN.md`](PLAN.md)입니다. 이 파일은 Claude Code가 같은 기준을 빠르게 찾도록 현재 상태만 요약합니다.

## 현재 기술 기준

- Java 21, Spring Boot 3.5.x, JPA/QueryDSL, Spring Security, JWT, Flyway
- MySQL 8, Redis
- Thymeleaf + HTMX + Alpine.js(CSP build) + 저장소 로컬 Tailwind 빌드 자산
- domain 기반 패키지: `controller`, `service`, `repository`, `entity`, `dto`
- 역할: `PRINCIPAL`, `TEACHER`, `PARENT`
- API prefix: `/api/v1/**`
- OSIV OFF, 기본 batch fetch size 100

## 시작 전 필수 확인

```bash
sed -n '1,220p' PLAN.md
sed -n '1,220p' docs/guides/developer-guide.md
sed -n '1,180p' docs/guides/env-contract.md
```

작업 시작 전에 `PLAN.md`에 목표·대상 파일·검증 명령·완료 조건을 반영합니다. 기존 dirty 변경은 사용자 소유로 보고 기능 단위로만 stage합니다.

## 실행·검증

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
./gradlew --no-daemon clean build
./gradlew --no-daemon integrationTest
./gradlew --no-daemon performanceSmokeTest
npm run frontend:build
npm run accessibility:templates
npm run security:inline-handlers
npm run security:view-errors
npm run docs:links
```

`local`/`demo`는 로컬 시연용이고, `prod`는 seed·Swagger·app-port management 노출을 허용하지 않는 fail-closed 계약을 유지합니다. 운영 설정 변경은 `deploy/`, `Dockerfile`, `scripts/`와 env contract를 함께 확인합니다.

## 구현 규칙

- Controller에서 받은 member ID를 service까지 전달하고, service에서 role·tenant·소유권·상태를 다시 검증합니다.
- 쓰기 service는 `@Transactional`, 조회 기본값은 `@Transactional(readOnly = true)`입니다.
- OSIV OFF이므로 View/Controller가 lazy loading에 의존하지 않습니다.
- 비즈니스 오류는 `BusinessException(ErrorCode)`로 반환하고, View flash message에 예외 원문을 노출하지 않습니다.
- 새 endpoint는 URL 권한과 service/method 권한, 성공·실패·교차 tenant 테스트를 함께 확인합니다.
- 변경은 `apply_patch`로 수행하고 강제 push·rebase·`--no-verify`는 사용하지 않습니다.

세부 규칙과 커밋 절차는 `AGENTS.md`를 우선합니다.
