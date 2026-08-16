프로젝트 루트에서 다음 순서로 실행하면 됩니다.

```bash
cd /Users/alex/project/kindergarten-erp/erp
```

1. Docker 의존성 실행

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
```

이미 `.env`가 있으면 `cp`는 생략하세요.

2. Java 21 설정

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

3. 로컬 서버 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

서버가 실행되면 보통 다음 주소로 접속합니다.

```text
http://localhost:8080
```

터미널을 계속 점유하므로, 서버를 켜둔 상태에서 새 터미널을 열어 확인하세요.

```bash
curl -I http://localhost:8080
curl -i http://localhost:8080/login
```

서버 로그를 실시간으로 확인하려면 실행 중인 터미널에서 확인하면 됩니다. 종료는 다음과 같습니다.

```text
Ctrl + C
```

Docker 의존성까지 종료하려면:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down
```

빠른 확인용으로는 다음 흐름이면 충분합니다.

```bash
cd /Users/alex/project/kindergarten-erp/erp
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
docker compose --env-file docker/.env -f docker/docker-compose.yml up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

이후 브라우저에서 [http://localhost:8080](http://localhost:8080)을 열면 됩니다.