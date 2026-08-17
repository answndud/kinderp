# [Spring Boot 포트폴리오] 22. 반 정원, 대기열, 입학 제안 워크플로우 설계하기

## 1. 이번 글에서 풀 문제

입학 신청 기능을 단순 CRUD로 만들면 보통 이렇게 됩니다.

- 신청 생성
- 원장이 승인
- 원생 생성

하지만 실제 유치원 운영을 생각하면 이 흐름은 너무 단순합니다.

- 반 정원이 꽉 차 있으면?
- 당장은 자리가 없지만 대기열에 넣어야 한다면?
- 나중에 자리가 생기면 입학 제안을 보내야 한다면?
- 제안이 일정 시간 내 수락되지 않으면?

KinderP는 이 문제를
**정원(capacity) + 대기열(waitlist) + 제안(offer) + 만료(expiry)** 모델로 풀었습니다.

## 2. 먼저 알아둘 개념

### 2-1. “정원”은 화면 정보가 아니라 제약 조건이다

`현재 몇 명인가`를 보여주는 것과
`더 받을 수 있는가`를 판단하는 것은 다릅니다.

정원은 단순 표시값이 아니라
도메인 규칙을 막는 제약 조건이어야 합니다.

### 2-2. 상태 전이로 생각해야 한다

입학 신청은 단순히 `PENDING -> APPROVED`가 아닙니다.

이 프로젝트는 아래 상태를 가집니다.

- `PENDING`
- `WAITLISTED`
- `OFFERED`
- `APPROVED`
- `OFFER_EXPIRED`
- `REJECTED`
- `CANCELLED`

### 2-3. “예약된 자리”도 정원 계산에 포함해야 한다

이미 원생으로 등록된 아이만 세면 부족합니다.
입학 제안을 받고 아직 수락 대기 중인 자리는 사실상 예약석입니다.

그래서 이 프로젝트는

- 재원 아동 수
- 활성 offer 수

를 함께 계산합니다.

### 2-4. 상태별로 누가 무엇을 할 수 있는지 먼저 보자

이 글은 상태가 많아서, “어떤 상태에서 어떤 행동이 가능한가”를 먼저 표로 보면 이해가 훨씬 쉽습니다.

| 상태 | 누가 다음 행동을 할 수 있는가 | 다음 가능 상태 |
|---|---|---|
| `PENDING` | 원장/교사 | `WAITLISTED`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `WAITLISTED` | 원장/교사 | `OFFERED`, `REJECTED`, `CANCELLED` |
| `OFFERED` | 학부모 또는 시스템 | `APPROVED`, `OFFER_EXPIRED` |
| `APPROVED` | 최종 상태 | 추가 승인 없음 |

### 2-5. 상세 조회 권한도 상태 전이만큼 중요하다

상태가 복잡해질수록
“누가 이 신청서를 볼 수 있는가”도 같이 고정해야 합니다.

현재 프로젝트 기준 정책은 아래와 같습니다.

| 주체 | 입학 신청 상세 조회 가능 여부 | 이유 |
|---|---|---|
| 신청한 학부모 본인 | 가능 | 본인 자녀 신청서이기 때문 |
| 같은 유치원의 원장 | 가능 | 승인/거절/offer 권한이 있기 때문 |
| 같은 유치원의 교사 | 가능 | 검토 큐와 상세를 함께 보는 reviewer 역할이기 때문 |
| 같은 유치원의 다른 학부모 | 불가 | 자녀 이름, 생년월일, 신청 메모는 개인정보이기 때문 |

## 3. 이번 글에서 다룰 파일

```text
- src/main/java/com/kinderp/domain/classroom/entity/Classroom.java
- src/main/java/com/kinderp/domain/classroom/service/ClassroomCapacityService.java
- src/main/java/com/kinderp/domain/kidapplication/entity/KidApplication.java
- src/main/java/com/kinderp/domain/kidapplication/entity/ApplicationStatus.java
- src/main/java/com/kinderp/domain/kidapplication/service/KidApplicationService.java
- src/main/resources/db/migration/V13__add_admission_workflow_attendance_requests_and_domain_audit.sql
- src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java
- src/test/java/com/kinderp/api/ClassroomApiIntegrationTest.java
- src/test/java/com/kinderp/api/KidApiIntegrationTest.java
- docs/COMPLETED.md#archive-003
```

## 4. 설계 구상

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> WAITLISTED
    PENDING --> APPROVED
    WAITLISTED --> OFFERED
    OFFERED --> APPROVED
    OFFERED --> OFFER_EXPIRED
    PENDING --> REJECTED
    WAITLISTED --> REJECTED
    PENDING --> CANCELLED
    WAITLISTED --> CANCELLED
```

핵심 기준은 아래였습니다.

1. 정원 검사는 별도 서비스에서 공통화한다
2. 반 정원은 재원 아동 수 + 활성 offer 수로 계산한다
3. 입학 신청은 상태 전이 메서드가 직접 자기 상태를 바꾼다
4. offer 만료는 스케줄러가 처리한다

## 5. 코드 설명

### 5-1. `Classroom`: 정원 규칙을 갖는 엔티티

[Classroom.java](../src/main/java/com/kinderp/domain/classroom/entity/Classroom.java)는
이제 단순히 반 이름만 갖는 엔티티가 아닙니다.

이 글에서 주목할 메서드는 아래입니다.

- `remainingSeats(...)`
- `canResizeTo(...)`

즉 정원 관련 계산 일부를 엔티티 자신이 책임집니다.

초보자는 서비스에 모든 계산을 몰아넣기 쉽지만,
반 자체의 규칙이라면 엔티티로 일부 끌고 오는 편이 읽기 좋습니다.

### 5-2. `ClassroomCapacityService`: 정원 계산을 공통 규칙으로 만든다

[ClassroomCapacityService.java](../src/main/java/com/kinderp/domain/classroom/service/ClassroomCapacityService.java)의 핵심 메서드는 아래입니다.

- `lockClassroom(...)`
- `summarize(...)`
- `validateSeatAvailable(...)`
- `validateCapacityReduction(...)`

여기서 가장 중요한 메서드는 `summarize(...)`입니다.

이 메서드는

- `kidRepository.countByClassroomIdAndDeletedAtIsNull(...)`
- `kidApplicationRepository.countActiveOffersByAssignedClassroomId(...)`

를 사용해

- 실제 재원 수
- 예약된 offer 수
- 남은 자리 수

를 모두 계산합니다.

즉 정원 계산이 여러 서비스에 흩어지지 않고 한 곳에 모입니다.

### 5-3. `KidApplication`: 신청 엔티티가 직접 상태 전이를 가진다

[KidApplication.java](../src/main/java/com/kinderp/domain/kidapplication/entity/KidApplication.java)의 핵심 메서드는 아래입니다.

- `placeOnWaitlist(...)`
- `offerSeat(...)`
- `acceptOffer(...)`
- `markOfferExpired()`
- `approveDirect(...)`

이 설계가 좋은 이유는
“어떤 상태에서 어떤 상태로 갈 수 있는가”가 서비스가 아니라
엔티티 메서드 이름으로 드러난다는 점입니다.

즉 상태 전이 규칙이 코드에서 읽힙니다.

### 5-4. `KidApplicationService.approve(...)`: 좌석이 있으면 바로 승인

[KidApplicationService.java](../src/main/java/com/kinderp/domain/kidapplication/service/KidApplicationService.java)의
`approve(...)`는 아래 순서로 동작합니다.

1. 신청서 잠금 조회
2. 처리자 검증
3. 반 조회
4. `classroomCapacityService.validateSeatAvailable(...)`
5. 실제 `Kid` 생성
6. `application.approveDirect(...)`
7. 대시보드 통계 캐시 무효화

즉 정원이 충분하면
전통적인 “바로 승인” 흐름도 여전히 지원합니다.
동시에 이 시점부터는 재원 원생 수와 학부모 수가 바뀌므로, 대시보드 캐시도 같이 비워야 합니다.

### 5-5. `placeOnWaitlist(...)`와 `offer(...)`

자리가 없거나 운영 정책상 대기열로 둘 때는 `placeOnWaitlist(...)`를 사용합니다.

나중에 자리가 생기면 `offer(...)`가 아래를 수행합니다.

1. 반 잠금/검증
2. 다시 정원 확인
3. `offerExpiresAt` 계산
4. `application.offerSeat(...)`

여기서 `offerExpiresAt`를 명시적으로 저장하는 것이 중요합니다.

그래야 “언제까지 답해야 하는가”가 도메인 데이터가 됩니다.

### 5-6. `acceptOffer(...)`: 학부모가 수락할 때만 최종 원생 생성

`acceptOffer(...)`는 아주 중요합니다.

이 메서드는

- parent 본인인지 확인
- 제안 만료 여부 확인
- 반을 다시 잠그고
- 실제 `Kid`를 생성한 뒤
- `application.acceptOffer(...)`
- 대시보드 캐시 무효화

를 수행합니다.

즉 제안을 보냈다고 바로 입학 완료가 아니라,
**수락 시점에만 확정 aggregate를 만든다**는 점이 핵심입니다.

### 5-7. `expireOffers()`: 스케줄러로 만료 처리

`KidApplicationService.expireOffers()`는 `@Scheduled`로 주기 실행됩니다.

이 메서드는

- 만료된 `OFFERED` 신청 조회
- `markOfferExpired()`
- 보호자 알림
- 시스템 감사 로그

를 수행합니다.

초보자가 배우기 좋은 포인트는
“시간 기반 상태 변화도 백엔드가 책임질 수 있다”는 점입니다.

## 6. 실제 흐름

```mermaid
sequenceDiagram
    participant Parent as 학부모
    participant Service as KidApplicationService
    participant Capacity as ClassroomCapacityService
    participant App as KidApplication

    Parent->>Service: 입학 신청
    Service->>App: create()
    Note over Service: 자리 없음
    Service->>App: placeOnWaitlist()
    Note over Service: 자리 생김
    Service->>Capacity: validateSeatAvailable()
    Service->>App: offerSeat()
    Parent->>Service: 제안 수락
    Service->>Capacity: lockClassroom()
    Service->>App: acceptOffer()
    Service->>Service: Kid 생성
```

## 7. 테스트로 검증하기

대표 테스트는 아래입니다.

- [KidApplicationApiIntegrationTest.java](../src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java)
  - waitlist / offer / accept / expire
- [ClassroomApiIntegrationTest.java](../src/test/java/com/kinderp/api/ClassroomApiIntegrationTest.java)
  - 정원 변경 검증
- [KidApiIntegrationTest.java](../src/test/java/com/kinderp/api/KidApiIntegrationTest.java)
  - 정원 초과 차단이나 실제 원생 생성 연동 검증

이 테스트들이 중요한 이유는
단순 JSON 응답이 아니라 **상태 전이와 좌석 규칙**을 검증하기 때문입니다.

## 8. 회고

이 기능은 CRUD로 보면 복잡해 보입니다.
하지만 실제 운영 문제를 그대로 옮겨 보면 오히려 더 자연스럽습니다.

- 자리가 없으면 대기열
- 자리가 나면 제안
- 일정 시간 안에 수락 안 하면 만료

즉 코드가 복잡해진 것이 아니라,
도메인 현실을 더 정확히 반영한 것입니다.

### 현재 구현의 한계

현재 waitlist/offer 모델은 **좌석 제약과 제안 만료**를 잘 표현하지만,
대기열 우선순위 정책을 아주 세밀하게 다루지는 않습니다. 예를 들어 형제 우선, 신청 시각 외 추가 가중치 같은 정책은 별도 규칙으로 더 확장해야 합니다.

## 9. 취업 포인트

- “정원은 단순 표시값이 아니라 재원 수 + 활성 offer 수를 함께 보는 실제 제약으로 설계했습니다.”
- “입학 신청을 단일 승인 버튼이 아니라 `WAITLISTED -> OFFERED -> APPROVED / OFFER_EXPIRED` 상태 머신으로 모델링했습니다.”
- “스케줄러와 정원 잠금, 재검증을 통해 시간 기반 상태 변화까지 백엔드 책임으로 닫았습니다.”

### 9-1. 1문장 답변

- “입학 신청을 단순 승인 버튼이 아니라 정원, 대기열, 제안, 만료를 가진 상태 머신으로 모델링해 실제 운영 흐름을 반영했습니다.”

### 9-2. 30초 답변

- “이 단계에서는 정원을 숫자 필드가 아니라 실제 제약 조건으로 끌어올렸습니다. `ClassroomCapacityService`가 재원 수와 활성 offer 수를 함께 계산하고, `KidApplication`은 `WAITLISTED -> OFFERED -> APPROVED / OFFER_EXPIRED` 상태 전이를 직접 가집니다. 또 `acceptOffer(...)`에서만 실제 `Kid`를 생성하게 해, 제안과 최종 승인 시점을 분리했습니다.”

### 9-3. 예상 꼬리 질문

- “왜 offer를 보냈다고 바로 승인하지 않았나요?”
- “왜 활성 offer도 좌석 계산에 포함했나요?”
- “대기열 우선순위는 어떻게 더 확장할 수 있나요?”

## 10. 시작 상태

- `07`, `10` 글까지 따라와서 `Classroom.capacity`와 입학 신청 기본 흐름이 이미 있어야 합니다.
- 이 글의 목표는 **정원을 숫자 필드에서 실제 운영 제약으로 끌어올리고, 입학 신청을 waitlist/offer 기반 상태 머신으로 바꾸는 것**입니다.
- 핵심은 두 가지입니다.
  - 좌석 가능 여부를 매번 재계산하고 잠그는 것
  - 제안(offer)과 최종 승인(approved)을 분리하는 것

## 11. 이번 글에서 바뀌는 파일

```text
- 정원 계산:
  - src/main/java/com/kinderp/domain/classroom/service/ClassroomCapacityService.java
  - src/main/java/com/kinderp/domain/classroom/entity/Classroom.java
- 입학 신청 상태 머신:
  - src/main/java/com/kinderp/domain/kidapplication/entity/KidApplication.java
  - src/main/java/com/kinderp/domain/kidapplication/controller/KidApplicationController.java
  - src/main/java/com/kinderp/domain/kidapplication/service/KidApplicationService.java
- 대시보드 연계:
  - src/main/java/com/kinderp/domain/dashboard/service/DashboardService.java
- 스키마:
  - src/main/resources/db/migration/V13__add_admission_workflow_attendance_requests_and_domain_audit.sql
- 검증:
  - src/test/java/com/kinderp/api/KidApplicationApiIntegrationTest.java
  - src/test/java/com/kinderp/api/ClassroomApiIntegrationTest.java
  - src/test/java/com/kinderp/api/KidApiIntegrationTest.java
- 결정 로그:
  - docs/COMPLETED.md#archive-003
```

## 12. 구현 체크리스트

1. `ClassroomCapacityService`에서 현재 재원 수와 활성 offer 수를 함께 계산합니다.
2. `KidApplication`에 `WAITLISTED`, `OFFERED`, `APPROVED`, `OFFER_EXPIRED` 상태 전이를 넣습니다.
3. `KidApplicationService.offer(...)`에서 제안 만료 시각과 좌석 재검증을 같이 처리합니다.
4. `acceptOffer(...)`에서만 실제 `Kid`를 생성해 입학을 확정합니다.
5. `expireOffers()` 스케줄러로 시간 기반 만료를 처리합니다.
6. 승인/offer 수락처럼 대시보드 숫자를 바꾸는 경로에서는 dashboard cache도 함께 비웁니다.
7. 통합 테스트로 waitlist, offer, accept, expire와 정원 규칙을 검증합니다.

## 13. 실행 / 검증 명령

```bash
./gradlew compileJava compileTestJava
# 현재 완성 저장소 기준 안정 검증
./gradlew --no-daemon integrationTest
```

성공하면 확인할 것:

- 산출물 체크리스트 기준으로 정원/대기열/offer 산출물이 맞는다
- 통합 스위트 안에서 `KidApplicationApiIntegrationTest`, `ClassroomApiIntegrationTest`, `KidApiIntegrationTest`가 통과한다
- 좌석 계산이 현재 재원 수와 활성 offer를 함께 반영한다
- 입학 신청이 waitlist/offer/accept/expire 흐름을 가진다

## 14. 산출물 체크리스트

- 새로 생긴 migration:
  - `V13__add_admission_workflow_attendance_requests_and_domain_audit.sql`
- 새로 생긴 주요 클래스:
  - `ClassroomCapacityService`
  - `KidApplicationWorkflowProperties`
  - `WaitlistKidApplicationRequest`
  - `OfferKidApplicationRequest`
  - `AcceptKidApplicationOfferRequest`
- 대표 검증 대상:
  - `KidApplicationApiIntegrationTest`
  - `ClassroomApiIntegrationTest`
  - `KidApiIntegrationTest`
- 입학 승인/offer 수락 경로가 `DashboardService.evictDashboardStatisticsCache(...)`까지 호출한다

## 15. 글 종료 체크포인트

- 정원이 단순 표시값이 아니라 운영 제약이라는 점을 설명할 수 있다
- offer와 최종 승인 사이에 별도 상태가 필요한 이유를 설명할 수 있다
- 시간 기반 상태 변화도 스케줄러와 감사 로그로 닫을 수 있다
- 실제 원생 생성 시점을 왜 `acceptOffer(...)`에 두는지 설명할 수 있다

## 16. 자주 막히는 지점

- 증상: 자리는 하나인데 offer를 여러 건 보내게 된다
  - 원인: 현재 재원 수만 보고 활성 offer 수를 좌석 계산에 반영하지 않았을 수 있습니다
  - 확인할 것: `ClassroomCapacityService.summarize(...)`, `validateSeatAvailable(...)`

- 증상: offer를 보냈는데 만료/수락 후 상태가 꼬인다
  - 원인: 실제 입학 확정 시점과 제안 시점을 같은 이벤트로 취급했을 수 있습니다
  - 확인할 것: `KidApplication.offerSeat(...)`, `acceptOffer(...)`, `markOfferExpired()`
