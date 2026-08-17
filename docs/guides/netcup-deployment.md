# netcup 단일 VPS 배포 가이드

이 문서는 TownPet과 같은 netcup VPS Lite 2 G12s에 KinderP를 배포하기 위한 실행 순서다. 상용 트래픽이 거의 없는 포트폴리오 환경이므로 MySQL·Redis·Spring Boot를 같은 서버에 두되, Compose network와 volume은 TownPet과 분리한다.

## 배포 자산

- `deploy/docker-compose.netcup.yml`: MySQL·Redis·ERP app·내부 Caddy
- `deploy/Caddyfile.netcup`: 공용 edge Caddy가 전달할 HTTP-only 내부 upstream
- `deploy/.env.netcup.example`: 이미지·DB·secret 변수 목록
- `scripts/validate-netcup-env.sh`: secret·hostname·TLS·password 정책 검사
- Resend SMTP: `NOTIFICATION_EMAIL_ENABLED=true`와 `SPRING_MAIL_*`를 운영 secret에 설정한다.
- `deploy/backup-mysql.sh`: checksum이 포함된 logical backup
- `deploy/restore-mysql.sh`: 명시적 destructive restore guard

## 서버 디렉터리

```text
/opt/kinderp/
├─ deploy/
└─ secrets/prod.env
```

`prod.env`는 Git에 넣지 않고 `chmod 600`으로 보관한다. Compose project directory에서 다음을 실행한다.

실제 secret 파일에서는 `PROD_ENV_FILE=/opt/kinderp/secrets/prod.env`처럼 절대 경로를 사용한다. 저장소의 example 값은 정적 `docker compose config` 검증을 위한 상대 경로다.

```bash
cd /opt/kinderp
chmod 600 secrets/prod.env
./scripts/validate-netcup-env.sh secrets/prod.env
docker network create edge || true
```

## 이미지와 환경변수

이미지는 GitHub Actions가 x86/amd64 대상으로 빌드해 GHCR에 올린다. VPS에서는 직접 Gradle build하지 않고 image tag를 갱신해 pull한다.

저장소 루트의 `.dockerignore`는 Gradle·Node 생성물, 로컬 환경 파일, 백업과 문서를 빌드 컨텍스트에서 제외한다. Dockerfile에 필요한 Gradle wrapper·설정·소스만 컨텍스트에 남기므로 CI 빌드가 로컬 작업물이나 secret에 의존하지 않는다.

```text
APP_IMAGE=ghcr.io/<owner>/kinderp:<commit-sha>
APP_VERSION=<commit-sha>
MYSQL_DATABASE=erp_db
MYSQL_USER=erpadmin
MYSQL_PASSWORD=<secret>
MYSQL_ROOT_PASSWORD=<secret>
REDIS_PASSWORD=<secret>
```

`CORS_ALLOWED_ORIGINS`는 실제 `https://erp.example.com`만 허용한다. `DB_URL`은 Compose가 내부 MySQL service로 주입한다.
Resend SMTP(`smtp.resend.com:587`, STARTTLS)는 인증·알림 이메일에 사용한다. `NOTIFICATION_EMAIL_ENABLED`가 true가 아니거나 SMTP secret이 없으면 validator가 배포를 거부한다.
배포 스크립트는 Compose를 기동하기 전에 `scripts/validate-netcup-env.sh`를 실행해 placeholder, root 계정, 약한 secret, HTTP CORS, SMTP 누락을 거부한다.

## 기동 순서

```bash
docker compose --env-file deploy/.env.netcup.example \\
  -f deploy/docker-compose.netcup.yml config

docker compose --env-file secrets/prod.env \\
  -f deploy/docker-compose.netcup.yml up -d mysql redis

docker compose --env-file secrets/prod.env \\
  -f deploy/docker-compose.netcup.yml up -d app caddy
```

`mysql`, `redis`, `app`의 health가 순서대로 통과해야 한다. TLS는 공용 edge Caddy에서만 종료하고 ERP 내부 Caddy는 HTTP upstream으로만 동작한다. 공용 edge Caddy는 원래 `Host`를 보존해 내부 Caddy의 site block이 `APP_DOMAIN`과 매칭되도록 한다. 공용 edge Caddy 외에는 `80`, `443`, `3306`, `6379`을 공개하지 않는다.

```bash
docker compose -f deploy/docker-compose.netcup.yml ps
docker compose -f deploy/docker-compose.netcup.yml logs --tail=200 app
docker inspect --format '{{.State.Health.Status}}' kinderp-app
docker exec kinderp-app wget -qO- http://127.0.0.1:9091/actuator/info
ss -lntp
```

## 백업과 복구

백업 전에 MySQL container와 database 이름을 확인한다.

```bash
set -a
. /opt/kinderp/secrets/prod.env
set +a
MYSQL_CONTAINER=kinderp-mysql \\
MYSQL_DATABASE="$MYSQL_DATABASE" \\
MYSQL_USER="$MYSQL_USER" \\
MYSQL_PASSWORD="$MYSQL_PASSWORD" \\
BACKUP_DIR=/opt/backups \\
./deploy/backup-mysql.sh
```

비밀번호를 명령행 리터럴로 쓰지 않고 저장소 밖 secret 파일에서 읽는다. 생성된 `mysql.sql.gz`, `manifest.txt`, `manifest.sha256`는 `deploy/encrypt-backup.sh`로 `age` 암호화한 뒤 VPS와 다른 failure domain에 복사한다.
dump나 checksum 단계가 실패하면 `backup-mysql.sh`는 이번 실행의 partial directory만 정리하고 종료한다. 이전 backup은 삭제하지 않는다.

```bash
AGE_RECIPIENT='age1...'
./deploy/encrypt-backup.sh /opt/backups/erp-YYYYMMDDTHHMMSSZ /opt/backups/erp-YYYYMMDDTHHMMSSZ.tar.gz.age
```

복구는 임시 MySQL volume에서 먼저 수행한다.

```bash
set -a
. /opt/kinderp/secrets/prod.env
set +a
ALLOW_DESTRUCTIVE_RESTORE=YES \\
BACKUP_ROOT=/opt/backups/erp-<backup-id> \\
MYSQL_CONTAINER=kinderp-mysql \\
MYSQL_DATABASE="$MYSQL_DATABASE" \\
MYSQL_USER="$MYSQL_USER" \\
MYSQL_PASSWORD="$MYSQL_PASSWORD" \\
./deploy/restore-mysql.sh
```

복구 전 checksum 검증이 실패하면 중단한다. 운영 DB에 바로 복구하지 말고 새 volume·임시 container에서 table count와 로그인 흐름을 확인한다.

## 업데이트와 롤백

1. TownPet·ERP DB paired backup을 만든다.
2. 새 `APP_IMAGE`와 `APP_VERSION`을 기록한다.
3. `docker compose pull` 후 app만 교체한다.
4. readiness와 ERP 로그인 smoke를 확인한다.
5. 실패하면 이전 image tag로 되돌린다.

Flyway가 이미 적용된 schema를 애플리케이션 image만으로 되돌릴 수 있는지 확인한다. 적용된 migration 파일을 삭제·수정하지 않는다.

## 완료 조건

- ERP app이 내부 MySQL·Redis에 연결된다.
- ERP 인증·알림 이메일이 Resend SMTP를 통해 발송된다.
- 공용 edge Caddy에서 `https://erp.example.com`으로 접근된다.
- 80/443 외부 포트는 공용 edge Caddy만 사용한다.
- MySQL backup checksum과 disposable restore가 실제로 통과한다.
- `docker stats`, `free -h`, `df -h` 결과를 TownPet과 함께 기록한다.
- 실행하지 않은 netcup·DNS·TLS·backup 결과를 완료로 주장하지 않는다.
