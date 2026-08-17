# 27. Spring Boot Doctor 100점 리팩토링 배치

## 배경

이번 배치의 목표는 기능을 새로 늘리는 것이 아니라, 이미 동작하는 KinderP 백엔드의 운영 안정성과 면접관 관점의 신뢰도를 높이는 것이었다.

`simplify`와 `spring-boot-doctor` 점검에서 확인한 핵심 문제는 다음과 같았다.

- paging parameter guard가 도메인별로 달랐다.
- URL 보안은 닫혀 있었지만 read endpoint의 method-level security 의도가 코드에서 균일하게 보이지 않았다.
- requester 없는 service overload가 남아 future regression 표면이 됐다.
- `AttendanceService`, `KidApplicationService`에 책임이 많이 몰려 있었다.
- calendar 권한 matrix는 서비스 코드에 있었지만 테스트로 충분히 고정되지 않았다.
- notepad 읽음 처리는 `notepad_read_confirm`로 이전됐지만 entity에 legacy `isRead` field가 남아 있었다.
- outbox 동시성 테스트에 `Thread.sleep`이 있었고 Spring Boot 3.5 기준 `@MockBean` deprecation warning이 남아 있었다.

## 진행 방식

한 번에 구조를 크게 바꾸지 않고 Phase 0~8로 나눴다. 각 phase는 compile과 targeted integration test를 통과한 뒤 다음 단계로 넘어갔다.

## 주요 변경

`PageRequests` 공통 helper를 추가해 음수 page, 0 이하 size, 과대 size를 일관되게 normalize/clamp했다. Announcement, Notepad, Auth/Domain audit query 경로에 적용했고, API 테스트로 size/page 결과를 고정했다.

읽기 및 member-owned endpoint에는 `@PreAuthorize("isAuthenticated()")`를 명시했다. 기존 `SecurityConfig.anyRequest().authenticated()`를 대체한 것이 아니라, URL 보안과 메서드 보안을 함께 드러내는 변경이다.

service layer에서는 requester 없는 public overload를 줄이고, controller와 외부 API가 requester 기반 canonical method를 타도록 정리했다. 권한 없는 조회/수정 path를 새로 열지 않고, 내부 helper는 private으로 낮췄다.

`AttendanceService`는 날짜 범위 계산, 출석 find-or-create, time fallback, 반-원생 검증을 private helper로 추출했다. 상태 전이와 dashboard cache eviction 정책은 유지했다.

`KidApplicationService`는 audit 기록 helper와 notification link 상수를 정리했다. 승인, 대기, 제안, 제안 수락, 거절, 취소의 side effect 순서는 바꾸지 않았다.

Calendar는 반복 일정 확장 로직을 건드리지 않고 권한 matrix 테스트를 보강했다. 유치원 전체 일정은 원장만 생성할 수 있고, 반 일정은 담임/연결 학부모 조회만 허용되며, 개인 일정은 작성자만 관리할 수 있음을 테스트로 고정했다.

Notepad는 entity의 deprecated `isRead` field와 method를 제거했다. DB column은 운영 호환성을 고려해 유지했고, 실제 읽음 상태는 계속 `notepad_read_confirm` 기반 DTO에서 계산한다.

테스트에서는 outbox 동시성 테스트의 `Thread.sleep(300L)`를 latch 기반 동기화로 바꿨고, `@MockBean`을 `@MockitoBean`으로 교체해 Spring Boot 3.5 deprecation warning을 제거했다.

## 검증

최종 검증은 아래 명령으로 완료했다.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew integrationTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew performanceSmokeTest
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew bootJar
docker compose --env-file docker/.env -f docker/docker-compose.yml config
git diff --check
```

모두 통과했다.

## Doctor 결과

- 변경 surface 기준 P0/P1/P2 신규 이슈 없음
- compile, full test, performance smoke, bootJar, compose config, diff check 통과
- Spring Boot Doctor 점수: `100/100`

## 남긴 판단

로컬 기본 shell의 `JAVA_HOME`은 여전히 깨진 경로지만, 저장소 변경 범위가 아니므로 전역 설정은 건드리지 않았다. 검증은 명령 단위 `JAVA_HOME`으로 containment했다.

`notepad.is_read` DB column은 즉시 drop하지 않았다. 운영 배포가 없더라도 migration은 되돌리기 어려운 변경이므로, 실제 column 제거는 별도 migration 계획과 rollback 전략을 둔 뒤 진행하는 것이 맞다.
