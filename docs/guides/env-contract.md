# Environment Contract

이 문서는 실행 환경별 필수 환경 변수와 안전한 기본값 규칙의 SSOT입니다.

## 공통 원칙

- `application.yml`은 fail-closed 기준선입니다.
- `SPRING_PROFILES_ACTIVE`를 명시하지 않으면 애플리케이션은 부팅되지 않습니다.
- 로컬 인프라용 값과 앱 프로세스용 시크릿은 분리합니다.
- Swagger/OpenAPI, app-port Prometheus, demo seed는 기본 공개가 아니라 명시적 opt-in입니다.
- Credentialed CORS allowed origins는 `app.security.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`로 환경별 명시합니다.
- 업무 기준 날짜·자동 기록 시간은 서버 JVM 기본 시간대와 무관하게 `Asia/Seoul`을 사용합니다.
- CORS 허용 헤더는 프론트 요청에 필요한 Content-Type, CSRF, 멱등 키, HTMX 헤더로 제한하며 wildcard header를 사용하지 않습니다.

## 1. local

로컬 개발은 아래 두 가지를 분리합니다.

1. Docker 인프라 환경변수
2. 앱 프로세스 환경변수

### Docker 인프라

- `docker/.env.example`를 `docker/.env`로 복사
- compose 실행은 `--env-file docker/.env`를 기본으로 사용
- 대상 값
  - `MYSQL_ROOT_PASSWORD`
  - `MYSQL_DATABASE`
  - `MYSQL_USER`
  - `MYSQL_PASSWORD`
  - `FLYWAY_DB_USERNAME`
  - `FLYWAY_DB_PASSWORD`
  - `GRAFANA_ADMIN_USER`
  - `GRAFANA_ADMIN_PASSWORD`
  - `DOCKER_BIND_HOST`
  - `MYSQL_PUBLISHED_PORT`
  - `REDIS_PUBLISHED_PORT`
  - `PROMETHEUS_PUBLISHED_PORT`
  - `GRAFANA_PUBLISHED_PORT`

### 앱 프로세스

- 필수
  - `SPRING_PROFILES_ACTIVE=local`
- 선택
  - `JWT_SECRET`
    - 비우면 local 전용 fallback secret 사용
  - `APP_SEED_ENABLED=true`
    - 로컬 시드 데이터가 필요할 때만 켭니다.
  - `APP_SEED_LOG_CREDENTIALS=true`
    - 시드 계정 정보를 로그에 남겨야 할 때만 켭니다.
  - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
  - `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`
  - `CORS_ALLOWED_ORIGINS`
    - 기본값은 `http://localhost:8080`

## 2. demo

면접/시연용 실행입니다.

- 필수
  - `SPRING_PROFILES_ACTIVE=demo`
- 기본 동작
  - local 설정을 포함합니다.
  - 시드 데이터가 자동으로 활성화됩니다.
  - Swagger/OpenAPI와 app-port Prometheus를 명시적으로 엽니다.
- 선택
  - `JWT_SECRET`
    - 비우면 local 계층의 demo용 fallback secret 사용
  - OAuth client 환경변수

## 3. prod

운영은 모든 핵심 값을 환경 변수로 명시해야 합니다.

### 필수

- `SPRING_PROFILES_ACTIVE=prod`
- `JWT_SECRET`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `FLYWAY_DB_USERNAME`
- `FLYWAY_DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PASSWORD`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `KAKAO_CLIENT_ID`
- `KAKAO_CLIENT_SECRET`
- `CORS_ALLOWED_ORIGINS`

`CORS_ALLOWED_ORIGINS`는 실제 HTTPS origin만 허용합니다.
예: `https://erp.example.com`
`*`, `http://...`, `localhost` 계열은 `prod` 부팅 안전 검증에서 차단합니다.

`DB_USERNAME`/`DB_PASSWORD`는 애플리케이션 DML 전용 계정이고, `FLYWAY_DB_USERNAME`/`FLYWAY_DB_PASSWORD`는 schema migration 전용 계정이다. 두 계정은 서로 달라야 하며 운영 secret도 별도로 발급한다.

### 선택

- `APP_VERSION`
  - 배포 이미지의 commit SHA를 Actuator `/actuator/info`에 표시합니다. CD가 자동 주입하며, 수동 배포에서는 이미지 태그와 동일한 값을 사용합니다.
- `REDIS_PORT`
- `MANAGEMENT_SERVER_PORT`
- `MANAGEMENT_SERVER_ADDRESS`
- `AUTH_RATE_LIMIT_LOGIN_WINDOW`, `AUTH_RATE_LIMIT_LOGIN_IP`, `AUTH_RATE_LIMIT_LOGIN_EMAIL`
- `AUTH_RATE_LIMIT_REFRESH_WINDOW`, `AUTH_RATE_LIMIT_REFRESH_IP`
- `AUTH_RATE_LIMIT_SIGNUP_WINDOW`, `AUTH_RATE_LIMIT_SIGNUP_IP`
- `NOTIFICATION_INCIDENT_WEBHOOK`
- `NOTIFICATION_INCIDENT_WEBHOOK_SECRET` (해당 webhook을 활성화할 때 필수)
- `NOTIFICATION_EMAIL_FROM`
- `NOTIFICATION_PUSH_WEBHOOK`
- `NOTIFICATION_PUSH_WEBHOOK_SECRET` (해당 webhook을 활성화할 때 필수)
- `NOTIFICATION_APP_WEBHOOK`
- `NOTIFICATION_APP_WEBHOOK_SECRET` (해당 webhook을 활성화할 때 필수)
- `NOTIFICATION_RATE_LIMIT_WINDOW`
- `NOTIFICATION_RATE_LIMIT_USER`
- `NOTIFICATION_RATE_LIMIT_IP`

### prod 안전 조건

- `app.seed.enabled=false`
- `springdoc.api-docs.enabled=false`
- `springdoc.swagger-ui.enabled=false`
- `app.security.management-surface.public-api-docs=false`
- `app.security.management-surface.expose-prometheus-on-app-port=false`
- `jwt.cookie-secure=true`
- CSRF `XSRF-TOKEN` 쿠키도 JWT 쿠키와 동일한 `Secure`/`SameSite` 정책을 사용한다.
- `CORS_ALLOWED_ORIGINS`에는 실제 HTTPS 서비스 origin만 둔다.
- rate limit은 기본적으로 login IP 15회/10분, login email 5회/10분, refresh IP 10회/5분, signup IP 10회/1시간이다. 외부 알림 생성 API는 사용자 30회/1분, IP 100회/1분으로 별도 제한한다. 조정 시 window와 limit을 함께 검토한다.
- rate-limit limit은 양수여야 하며, 잘못된 값은 애플리케이션 설정 바인딩 단계에서 부팅 실패로 차단한다.

## 4. 테스트

- 테스트는 `@ActiveProfiles("test")`를 사용합니다.
- JWT/OAuth/Redis/Testcontainers용 테스트 전용 값은 `src/test/resources/application-test.yml`에 고정합니다.

## 5. 체크리스트

배포 전에는 아래를 반드시 확인합니다.

1. `SPRING_PROFILES_ACTIVE`가 명시돼 있는가
2. `JWT_SECRET`가 실제 시크릿인가
3. Swagger/OpenAPI가 prod에서 비활성화돼 있는가
4. Prometheus가 app port가 아니라 management plane 또는 내부 경로로만 노출되는가
5. `app.seed.enabled`가 prod에서 꺼져 있는가
6. `CORS_ALLOWED_ORIGINS`가 운영 도메인의 HTTPS origin으로 제한돼 있는가

## 6. 로컬 compose 기본값

- local/demo Docker compose는 기본적으로 `DOCKER_BIND_HOST=127.0.0.1`로만 포트를 엽니다.
- 기본 published port는 아래와 같습니다.
  - MySQL `3306`
  - Redis `6379`
  - Prometheus `9090`
  - Grafana `3000`
- 외부 공개가 필요하면 `docker/.env`에서 값을 명시적으로 바꾸고, demo/runbook 문서도 같이 수정해야 합니다.
