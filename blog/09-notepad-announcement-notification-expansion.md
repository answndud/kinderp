# [Spring Boot 포트폴리오] 09. 알림장, 공지, 알림으로 기능을 어떻게 확장했는가

## 1. 이번 글에서 풀 문제

원생과 출석까지 만들었다면 다음 질문이 생깁니다.

- 교사와 학부모는 어떻게 소통할까?
- 유치원 전체에 공지를 보내려면 무엇이 필요할까?
- 이벤트가 생겼을 때 사용자에게 어떻게 알려 줄까?

이 프로젝트는 이 문제를 세 도메인으로 풀었습니다.

- `Notepad`
- `Announcement`
- `Notification`

겉으로 보면 셋 다 “메시지”처럼 보입니다. 하지만 역할은 다릅니다.

- 알림장: 교사와 학부모의 일상 소통
- 공지사항: 유치원 전체 공지
- 알림: 시스템이 특정 사용자에게 보내는 이벤트

즉, 이 셋을 분리해야 도메인 규칙이 깔끔해집니다.

## 2. 먼저 알아둘 개념

### 2-1. 도메인별 메시지 성격 구분

모든 메시지를 하나의 테이블로 몰아넣으면 나중에 규칙이 섞입니다.

예를 들어

- 알림장은 읽음 확인이 중요하고
- 공지사항은 중요 공지와 조회수 집계가 중요하고
- 알림은 수신자별 읽음/삭제가 중요합니다

즉, 목적이 다르므로 도메인도 분리해야 합니다.

### 2-2. Fan-out

한 이벤트를 여러 사용자에게 퍼뜨리는 방식을 fan-out이라고 생각하면 됩니다.

이 프로젝트에서는

- 알림장 작성 시 여러 학부모에게 알림 전송
- 공지 작성 시 여러 역할 사용자에게 알림 전송

이 fan-out 패턴으로 이어집니다.

### 2-3. Soft Delete와 읽음 처리

사용자 알림은 보통 바로 물리 삭제하지 않고 soft delete를 사용합니다.
또한 읽음 여부는 개별 사용자 상태로 관리해야 합니다.

### 2-4. 어떤 상황에 어떤 도메인을 쓸지 먼저 구분하자

셋 다 메시지처럼 보이기 때문에, 쓰임새를 먼저 나누면 훨씬 이해가 쉽습니다.

| 상황 | 쓰는 도메인 | 이유 |
|---|---|---|
| 교사가 특정 원생/반 학부모에게 생활 내용을 보냄 | `Notepad` | 범위와 소통 대상이 중요하기 때문 |
| 유치원 전체에 중요한 공지를 띄움 | `Announcement` | 중요도, 조회수, 전체 공지가 필요하기 때문 |
| 시스템이 특정 사용자에게 이벤트를 알려 줌 | `Notification` | 수신자별 읽음/삭제 상태가 필요하기 때문 |

## 3. 이번 글에서 다룰 파일

```text
- src/main/java/com/kinderp/domain/notepad/entity/Notepad.java
- src/main/java/com/kinderp/domain/notepad/service/NotepadService.java
- src/main/java/com/kinderp/domain/announcement/entity/Announcement.java
- src/main/java/com/kinderp/domain/announcement/service/AnnouncementService.java
- src/main/java/com/kinderp/domain/notification/entity/Notification.java
- src/main/java/com/kinderp/domain/notification/service/NotificationService.java
- src/test/java/com/kinderp/api/NotepadApiIntegrationTest.java
- src/test/java/com/kinderp/api/AnnouncementApiIntegrationTest.java
- src/test/java/com/kinderp/api/NotificationApiIntegrationTest.java
- docs/COMPLETED.md#archive-001
```

## 4. 설계 구상

이 세 도메인의 관계는 아래처럼 볼 수 있습니다.

```mermaid
flowchart TD
    A["Teacher / Principal"] --> B["Notepad"]
    A --> C["Announcement"]
    B --> D["Notification"]
    C --> D
    D --> E["Parent / Teacher / Principal"]
```

핵심 설계 기준은 아래였습니다.

1. 알림장은 범위가 있다: 전체 / 반 / 원생
2. 공지는 유치원 단위로 관리한다
3. 알림은 수신자 중심으로 관리한다
4. 소통 도메인에서 발생한 이벤트는 Notification으로 fan-out할 수 있어야 한다

## 5. 코드 설명

### 5-1. `Notepad`: 전체/반/원생 범위를 가진 알림장

[Notepad.java](../src/main/java/com/kinderp/domain/notepad/entity/Notepad.java)의 핵심은 범위 분기입니다.

생성 메서드는 아래 세 개입니다.

- `createClassroomNotepad(...)`
- `createKidNotepad(...)`
- `createGlobalNotepad(...)`

그리고 범위를 확인하는 메서드도 따로 있습니다.

- `isClassroomNotepad()`
- `isKidNotepad()`
- `isGlobalNotepad()`

즉, 알림장은 “하나의 도메인”이지만 내부에 세 가지 발행 범위가 있습니다.

### 5-2. `NotepadService`: 생성과 알림 fan-out을 같이 다룬다

[NotepadService.java](../src/main/java/com/kinderp/domain/notepad/service/NotepadService.java)의 핵심 메서드는 아래입니다.

- `createClassroomNotepad(...)`
- `createGlobalNotepad(...)`
- `createNotepad(...)`
- `getNotepadDetail(...)`

이 서비스의 중요한 포인트는 `notifyParentsAboutNotepad(...)`입니다.

즉, 알림장 저장만 하지 않고

- 원생별이면 해당 원생의 학부모
- 반별이면 반 소속 원생들의 학부모
- 전체면 해당 유치원 학부모들

에게 알림을 fan-out 합니다.

### 5-3. `Announcement`: 유치원 전체 공지와 중요 공지

[Announcement.java](../src/main/java/com/kinderp/domain/announcement/entity/Announcement.java)의 핵심 생성 메서드는 아래입니다.

- `create(...)`
- `createImportant(...)`

핵심 비즈니스 메서드는 아래입니다.

- `update(...)`
- `setImportant(...)`
- `toggleImportant()`
- `incrementViewCount()`
- `softDelete()`

즉, 공지사항은 단순 게시글이 아니라

- 중요도
- 조회수
- soft delete

를 함께 다룹니다.

### 5-4. `AnnouncementService`: 공지 작성이 대시보드와 감사 로그까지 연결된다

[AnnouncementService.java](../src/main/java/com/kinderp/domain/announcement/service/AnnouncementService.java)의 핵심 메서드는 아래입니다.

- `createAnnouncement(...)`
- `getAnnouncement(...)`
- `updateAnnouncement(...)`
- `deleteAnnouncement(...)`

특히 이 서비스는 아래와 연결됩니다.

- `notificationService.notifyWithLink(...)`
- `dashboardService.evictDashboardStatisticsCache(...)`
- `domainAuditLogService.record(...)`

즉, 공지사항은 단독 기능이 아니라

- 알림
- 대시보드
- 업무 감사 로그

와 연결된 허브 기능입니다.

### 5-5. `Notification`: 수신자 중심의 이벤트 박스

[Notification.java](../src/main/java/com/kinderp/domain/notification/entity/Notification.java)의 핵심 생성 메서드는 아래입니다.

- `create(...)`
- `createWithRelatedEntity(...)`
- `createWithLink(...)`

핵심 상태 메서드는 아래입니다.

- `markAsRead()`
- `markAsUnread()`
- `softDelete()`

즉, 알림은 “어떤 이벤트가 누구에게 갔는가”를 표현하는 도메인입니다.

### 5-6. `NotificationService`: 내부 이벤트를 사용자 알림으로 바꾸는 계층

[NotificationService.java](../src/main/java/com/kinderp/domain/notification/service/NotificationService.java)의 핵심 메서드는 아래입니다.

- `notify(...)`
- `notifyWithLink(...)`
- `notifyWithRelatedEntity(...)`
- `getNotifications(...)`
- `markAsRead(...)`
- `delete(...)`

즉, 다른 도메인이 직접 알림 엔티티를 만지지 않고
NotificationService를 통해 사용자 알림으로 변환됩니다.

## 6. 실제 흐름

이 세 도메인은 아래처럼 이어집니다.

```mermaid
sequenceDiagram
    participant Teacher as 교사
    participant Notepad as NotepadService
    participant Announcement as AnnouncementService
    participant Notification as NotificationService
    participant Parent as 학부모

    Teacher->>Notepad: 알림장 작성
    Notepad->>Notification: 학부모 알림 fan-out
    Announcement->>Notification: 공지 알림 fan-out
    Parent->>Notification: 목록 조회 / 읽음 처리 / 삭제
```

즉, “소통”과 “이벤트 전달”을 분리한 것입니다.

## 7. 테스트로 검증하기

관련 통합 테스트는 아래입니다.

- `NotepadApiIntegrationTest`
- `AnnouncementApiIntegrationTest`
- `NotificationApiIntegrationTest`

그리고 설계 의도는 아래 결정 로그에 남아 있습니다.

- [phase05_notepad.md](../docs/COMPLETED.md#archive-001)
- [phase06_announcement.md](../docs/COMPLETED.md#archive-001)
- [phase08_notification.md](../docs/COMPLETED.md#archive-001)

즉, 알림장/공지/알림이 단순 CRUD가 아니라
실제 사용자 소통 흐름으로 검증되고 있습니다.

## 8. 회고

이 단계에서 중요한 교훈은 하나입니다.

**비슷해 보이는 기능도 목적이 다르면 도메인을 분리해야 한다**는 점입니다.

알림장, 공지, 알림을 하나로 뭉쳤다면

- 읽음 규칙
- 작성 권한
- 조회 범위
- fan-out 방식

이 다 섞였을 것입니다.

분리했기 때문에 이후 outbox, 감사 로그, 대시보드 같은 확장도 더 쉬워졌습니다.

### 현재 구현의 한계

이 글 단계의 알림 전달은 **도메인 분리와 fan-out 구조**에 집중합니다.
즉, 외부 채널 재시도나 dead-letter 같은 전달 신뢰성 문제는 아직 닫히지 않았고, 그 부분은 후반 outbox 글에서 보강합니다.

## 9. 취업 포인트

면접에서는 이렇게 설명할 수 있습니다.

- “알림장, 공지, 알림을 모두 메시지로 보지 않고 목적별 도메인으로 분리했습니다.”
- “알림장과 공지 작성 이벤트는 NotificationService를 통해 사용자 알림으로 fan-out 되도록 설계했습니다.”
- “공지사항은 대시보드 캐시 무효화와 업무 감사 로그까지 연결되는 허브 기능으로 키웠습니다.”

### 9-1. 1문장 답변

- “비슷해 보이는 메시지 기능을 `Notepad`, `Announcement`, `Notification`으로 분리해 소통 규칙과 전달 규칙이 서로 섞이지 않게 설계했습니다.”

### 9-2. 30초 답변

- “이 단계에서는 소통 도메인을 목적별로 나누는 데 집중했습니다. `Notepad`는 원생/반/전체 범위를 가진 생활 소통이고, `Announcement`는 유치원 전체 공지와 중요 공지를 다루며, `Notification`은 수신자별 읽음/삭제 상태를 가진 이벤트 박스입니다. 그리고 알림장/공지 작성 이벤트는 `NotificationService`를 통해 fan-out 되게 만들어 이후 outbox나 감사 로그 같은 확장도 자연스럽게 붙을 수 있게 했습니다.”

### 9-3. 예상 꼬리 질문

- “왜 공지와 알림장을 하나의 게시판으로 만들지 않았나요?”
- “fan-out을 Notification 도메인으로 분리한 이유는 무엇인가요?”
- “읽음 처리와 soft delete를 왜 알림 도메인에 따로 둬야 하나요?”

## 10. 시작 상태

- `08` 글까지 따라와서 회원/원생/반/출석 구조가 이미 있어야 합니다.
- 이 글의 목표는 **소통 기능을 하나의 게시판으로 뭉개지 않고, 목적에 따라 알림장/공지/알림으로 분리하는 것**입니다.
- 이후 outbox, 감사 로그, 대시보드와 연결되기 때문에 초기에 도메인 경계를 잘 나눠 두는 것이 중요합니다.

## 11. 이번 글에서 바뀌는 파일

```text
- 알림장:
  - src/main/java/com/kinderp/domain/notepad/entity/Notepad.java
  - src/main/java/com/kinderp/domain/notepad/controller/NotepadController.java
  - src/main/java/com/kinderp/domain/notepad/service/NotepadService.java
- 공지:
  - src/main/java/com/kinderp/domain/announcement/entity/Announcement.java
  - src/main/java/com/kinderp/domain/announcement/controller/AnnouncementController.java
  - src/main/java/com/kinderp/domain/announcement/service/AnnouncementService.java
- 알림:
  - src/main/java/com/kinderp/domain/notification/entity/Notification.java
  - src/main/java/com/kinderp/domain/notification/controller/NotificationController.java
  - src/main/java/com/kinderp/domain/notification/service/NotificationService.java
- 검증:
  - src/test/java/com/kinderp/api/NotepadApiIntegrationTest.java
  - src/test/java/com/kinderp/api/AnnouncementApiIntegrationTest.java
  - src/test/java/com/kinderp/api/NotificationApiIntegrationTest.java
- 결정 로그:
  - docs/COMPLETED.md#archive-001
```

## 12. 구현 체크리스트

1. 알림장은 반/원생/전체 범위를 가진 소통 도메인으로 구현합니다.
2. 공지는 유치원 전체 공지와 중요 공지, 열람 수 집계를 고려해 분리합니다.
3. 알림은 읽음/안읽음/soft delete를 가진 사용자 이벤트 도메인으로 둡니다.
4. `NotificationService`를 통해 다른 도메인 이벤트를 사용자 알림으로 fan-out 합니다.
5. 알림장/공지/알림의 조회 범위와 권한을 각각 다르게 설계합니다.
6. 통합 테스트로 작성, 수정, 조회, 읽음 처리, fan-out 흐름을 검증합니다.

## 13. 실행 / 검증 명령

```bash
./gradlew compileJava compileTestJava
./gradlew --no-daemon integrationTest
```

성공하면 확인할 것:

- 통합 스위트 안에서 `NotepadApiIntegrationTest`, `AnnouncementApiIntegrationTest`, `NotificationApiIntegrationTest`가 통과한다
- 알림장과 공지가 각각의 권한/범위로 생성된다
- 작성 이벤트가 사용자 알림으로 fan-out 된다
- 알림 읽음 처리와 soft delete가 분리된 규칙으로 동작한다

## 14. 산출물 체크리스트

- `Notepad`, `Announcement`, `Notification` 엔티티와 각 서비스가 존재한다
- `NotepadController`, `AnnouncementController`, `NotificationController`가 연결돼 있다
- `NotepadApiIntegrationTest`, `AnnouncementApiIntegrationTest`, `NotificationApiIntegrationTest`가 통합 스위트에 포함된다
- fan-out 진입점이 `NotificationService`로 모여 있다

## 15. 글 종료 체크포인트

- 알림장, 공지, 알림을 왜 분리했는지 설명할 수 있다
- 알림 fan-out이 `NotificationService`로 모여 있다
- 읽음 규칙, 조회 범위, 작성 권한이 도메인별로 분리돼 있다
- 이후 outbox와 감사 로그가 이 구조에 어떻게 올라가는지 설명할 수 있다

## 16. 자주 막히는 지점

- 증상: 알림장과 공지를 같은 게시물처럼 다루게 된다
  - 원인: 작성 대상, 조회 범위, 읽음 규칙이 다르다는 점을 놓쳤을 수 있습니다
  - 확인할 것: `Notepad`와 `Announcement`의 생성 팩토리/조회 API 차이

- 증상: 다른 도메인이 알림 엔티티를 직접 생성해 결합도가 높아진다
  - 원인: 공통 알림 진입점을 `NotificationService`로 모으지 않았을 수 있습니다
  - 확인할 것: `NotificationService.notify(...)`, `notifyWithLink(...)`, `notifyWithRelatedEntity(...)`
