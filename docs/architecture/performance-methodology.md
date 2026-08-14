# 성능 측정 방법과 증거 범위

성능 수치는 단일 숫자를 과장하지 않기 위해 목적별로 분리한다.

## 1. JVM query-count story

| 시나리오 | 데이터 조건 | warm-up | 측정값 | 현재 테스트 |
| --- | --- | --- | --- | --- |
| Notepad 목록 | 기본 fixture + 알림장 80건, page size 20, 읽음 확인 40건 | legacy/optimized 각 1회 | Hibernate query count, elapsed ms | `NotepadPerformanceStoryTest` |
| Notepad 대규모 fixture | 기본 fixture + 알림장 1,000건, page size 20 | optimized 1회 | 고정 query budget, elapsed ms | `NotepadPerformanceStoryTest` |
| Dashboard 집계 | 기본 fixture의 유치원·원생·회원·출결·공지 | legacy/optimized 각 1회 | Hibernate query count, elapsed ms | `DashboardPerformanceStoryTest` |
| Dashboard cache | 같은 유치원 statistics key | cache clear 후 miss/hit | Hibernate query count, elapsed ms | `DashboardPerformanceStoryTest` |
| Outbox timeline | Outbox 80건, status/channel filter, page size 20 | 없음 | page + count query budget, elapsed ms | `NotificationOutboxPerformanceSmokeTest` |
| Outbox 대규모 fixture | Outbox 1,000건, status/channel filter, page size 20 | 없음 | 고정 page + count query budget, elapsed ms | `NotificationOutboxPerformanceSmokeTest` |

이 테스트의 목적은 절대적인 처리량이 아니라 N+1 제거, 집계 쿼리 전환, cache hit, 운영 검색 쿼리 예산의 회귀를 빠르게 잡는 것이다. 따라서 이 숫자를 고트래픽 처리량으로 표현하지 않는다.

### 최신 로컬 JVM smoke 관측값

2026-08-14, Java 21·Docker MySQL 8/Redis 7·demo profile에서 `./gradlew performanceSmokeTest`를 실행했다. 테스트는 8개 모두 통과했으며, 대표 관측값은 다음과 같다.

| 경로 | 개선 전/조건 | 개선 후/조건 | 관측값 |
| --- | --- | --- | --- |
| Dashboard 집계 | legacy 13 queries / 13ms | optimized 5 queries / 5ms | 쿼리 61.5% 감소 |
| Dashboard cache | cache miss 5 queries / 5ms | cache hit 0 queries / 0ms | 동일 key 재조회 DB 접근 없음 |
| Dashboard 대규모 fixture | 원생 1,001건 | optimized 5 queries / 57ms | fixture 증가에도 query budget 고정 |
| Notepad 목록 | legacy 22 queries / 17ms | optimized 5 queries / 6ms | 쿼리 77.3% 감소 |
| Notepad 대규모 fixture | 알림장 1,001건 | optimized 5 queries / 18ms | fixture 증가에도 query budget 고정 |
| Outbox timeline | status/channel·keyword filter | 각 1 query / 5ms, 1ms | page + count budget 유지 |
| Outbox 대규모 fixture | Outbox 1,000건 | 1 query / 4ms | fixture 증가에도 query budget 고정 |
| Audit console | list/export | filtered list/export | 2~3 queries / 4~26ms |

이 값은 단일 로컬 JVM smoke의 관측값이므로 운영 p95나 처리량으로 해석하지 않는다. HTTP 부하 p95, 대규모 fixture 선형성, local MySQL `EXPLAIN`을 각각 별도 증거로 남겼고, 운영 DB 결과는 배포 후 다시 확인한다.

## 2. HTTP 부하 시나리오

재현 스크립트: [`scripts/performance/k6-auth-notepad-dashboard.js`](../../scripts/performance/k6-auth-notepad-dashboard.js)

| 항목 | 설정 |
| --- | --- |
| Notepad | constant-vus 10, 30초, page 0/size 20 |
| Dashboard | constant-vus 5, 30초 |
| 반복 간격 | 1초 sleep |
| 인증 | 각 iteration에서 cookie JWT 로그인 후 endpoint 호출 |
| 성공 기준 | HTTP error rate 1% 미만 |
| p95 기준 | 전체 HTTP duration 800ms 미만 |

2026-08-14 Docker k6 실행 결과는 15 VU(알림장 10, 대시보드 5), 각 30초, iteration당 1초 sleep, 1,068 HTTP requests였다. 오류율은 0.00%였고, 전체 HTTP duration은 p95 362.13ms / p99 464.86ms였다. 세부 endpoint Trend는 Notepad p95 69.36ms / p99 99.52ms, Dashboard p95 25.39ms / p99 29.96ms였다. 이 결과는 특정 로컬 실행 조건의 관측값이며, 운영 트래픽 보장을 의미하지 않는다.

## 3. 실제 MySQL EXPLAIN 비교

Outbox timeline의 동일한 `status = 'DEAD_LETTER'`, `channel = 'EMAIL'`, `ORDER BY dead_lettered_at DESC, id DESC` 조회를 migration V17 전후로 확인했다.

| 상태 | 선택 인덱스 | 정렬 | 판단 |
| --- | --- | --- | --- |
| V16 이전 | `idx_notification_outbox_timeline(status, channel, created_at, id)` | `Using filesort` | 필터는 인덱스를 사용하지만 dead-letter 시간 정렬을 다시 수행 |
| V17 이후 | `idx_notification_outbox_dead_letter_timeline(status, channel, dead_lettered_at, id)` | `Backward index scan` | 필터와 최신순 정렬을 같은 인덱스로 처리 |

이 변경은 `notification_outbox`의 기존 인덱스를 제거하지 않는 forward migration이며, 검색어(`LIKE '%...%'`)가 포함된 경우에는 별도의 전문 검색 인덱스 없이 filesort/scan이 남을 수 있다. 검색어 경로를 무리하게 일반 인덱스로 포장하지 않고, 현재 운영 화면의 상태·채널 필터를 우선 최적화했다.

## 4. 실행 전 확인해야 할 조건

성능 결과를 새로 기록할 때 아래 조건을 함께 남긴다.

- Java·Spring Boot·MySQL·Redis 버전
- CPU·메모리와 DB connection pool
- fixture 데이터 건수와 인덱스 상태
- warm-up 횟수와 측정 횟수
- query count와 p50/p95/p99
- 실패율과 timeout
- 개선 전후의 동일한 요청 조건
- 주요 SQL의 `EXPLAIN` 결과와 선택한 인덱스

## 5. 아직 없는 증거

- 운영 MySQL의 실제 `EXPLAIN` 결과
- 실제 배포 환경에서의 네트워크·TLS 포함 p95

따라서 현재 포트폴리오의 정확한 표현은 “N+1과 집계 쿼리를 query count/elapsed time으로 개선하고, 대규모 local fixture·local MySQL `EXPLAIN`·CSRF를 포함한 제한된 k6 시나리오에서 p95/p99를 관측했다”이다. 운영 환경의 네트워크·TLS·DB 차이는 실제 배포 후 별도 측정한다.
