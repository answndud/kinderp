# [Spring Boot 포트폴리오] 18. OAuth2와 소셜 계정 lifecycle을 안전하게 설계하기

## 1. 이번 글에서 풀 문제

소셜 로그인은 처음 붙일 때는 쉬워 보입니다.

- 구글에서 정보 받아 오고
- 회원 없으면 가입시키고
- 있으면 로그인시키면 끝 아닌가?

하지만 실제로는 훨씬 복잡합니다.

- 기존 이메일 계정과 충돌하면 어떻게 할까?
- 로그인과 “소셜 계정 연결”은 같은 흐름일까?
- 소셜 계정을 해제할 때 마지막 로그인 수단이 사라지면 어떻게 할까?
- 같은 provider의 다른 계정으로 바꾸는 것은 허용할까?

KinderP는 이 문제를 꽤 깊게 다뤘습니다.

- 자동 소셜 가입
- 명시적 소셜 계정 연결
- 로컬 비밀번호를 처음 심기(bootstrap)
- 안전한 unlink
- 같은 provider 계정 바꾸기 금지(식별자 불변성)

즉, 소셜 로그인 자체보다 **계정 lifecycle 정책**이 핵심이었습니다.

## 2. 먼저 알아둘 개념

### 2-1. 소셜 로그인과 소셜 연결은 다르다

- 소셜 로그인
  - 소셜 계정으로 즉시 인증
- 소셜 연결
  - 이미 로그인한 내 계정에 소셜 provider를 추가

이 둘을 같은 흐름으로 처리하면 정책이 꼬이기 쉽습니다.

### 2-2. 계정 충돌

소셜 provider가 준 이메일이 이미 로컬 계정과 충돌할 수 있습니다.

이때 자동으로 합치면 위험합니다.
그래서 이 프로젝트는 자동 연결을 막고 충돌로 처리합니다.

### 2-3. 소셜 계정 불변성

같은 provider의 계정을 다른 계정으로 교체 가능하게 두면
계정 탈취와 복구가 매우 어려워집니다.

그래서 이 프로젝트는 “같은 provider 교체 금지”를 정책으로 택했습니다.

### 2-4. 상황별 정책표

이 글은 정책이 많아서, 아래 표를 먼저 보는 편이 이해가 쉽습니다.

| 상황 | 허용 여부 | 사용자에게 보이는 결과 | 이유 |
|---|---|---|---|
| 처음 소셜 로그인, 기존 계정 없음 | 허용 | 신규 소셜 회원 생성 | 처음 진입 경로이기 때문 |
| 이미 연결된 소셜 계정으로 로그인 | 허용 | 바로 로그인 | 이미 검증된 연결이 있기 때문 |
| 기존 로컬 계정과 이메일 충돌 | 금지 | 충돌 안내 후 수동 연결 유도 | 자동 병합은 위험하기 때문 |
| 로그인된 계정에 새 provider 연결 | 허용 | settings에서 연결 성공 | 명시적 의도가 있기 때문 |
| 마지막 로그인 수단만 남은 상태에서 unlink | 금지 | 해제 거부 | 계정 잠금을 막아야 하기 때문 |
| 같은 provider의 다른 계정으로 교체 | 금지 | 연결 거부 | 계정 식별 안정성을 지키기 위해서 |

## 3. 이번 글에서 다룰 파일

```text
- src/main/java/com/erp/global/security/oauth2/OAuth2AuthenticationSuccessHandler.java
- src/main/java/com/erp/global/security/oauth2/OAuth2LinkSessionService.java
- src/main/java/com/erp/domain/auth/controller/SocialAccountLinkController.java
- src/main/java/com/erp/domain/auth/service/SocialAccountLinkService.java
- src/main/java/com/erp/domain/member/entity/Member.java
- src/main/java/com/erp/domain/member/entity/MemberSocialAccount.java
- src/main/resources/db/migration/V8__normalize_member_social_accounts.sql
- src/main/resources/db/migration/V9__preserve_social_account_history.sql
- src/test/java/com/erp/global/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
- src/test/java/com/erp/api/MemberApiIntegrationTest.java
- docs/COMPLETED.md#archive-002
```

## 4. 설계 구상

```mermaid
flowchart TD
    A["OAuth2 login"] --> B["OAuth2AuthenticationSuccessHandler"]
    B --> C["기존 social 계정 로그인"]
    B --> D["신규 social 회원 등록"]
    B --> E["명시적 link intent 처리"]
    E --> F["SocialAccountLinkService"]
    F --> G["Member / MemberSocialAccount"]
```

핵심 기준은 아래였습니다.

1. 로그인과 연결 의도(link intent)를 구분한다
2. 이메일 충돌은 자동 연결하지 않는다
3. 소셜 계정은 별도 테이블로 정규화한다
4. unlink는 안전한 다른 로그인 수단이 있을 때만 허용한다
5. 같은 provider의 다른 계정으로 교체는 허용하지 않는다

## 5. 코드 설명

### 5-1. `OAuth2AuthenticationSuccessHandler`: 단순 redirect가 아니라 정책 엔진

[OAuth2AuthenticationSuccessHandler.java](../src/main/java/com/erp/global/security/oauth2/OAuth2AuthenticationSuccessHandler.java)의 핵심 메서드는 아래입니다.

- `onAuthenticationSuccess(...)`
- `handleSocialLink(...)`
- `registerSocialMember(...)`
- `clearTemporaryOAuthSession(...)`
- `resolveRedirect(...)`
- `mapLinkErrorReason(...)`

즉, 이 핸들러는 단순 redirect 코드가 아니라

- 신규 소셜 가입
- 기존 소셜 로그인
- 연결 의도(link intent) 처리
- 충돌 차단
- 감사 로그

를 한 번에 다룹니다.

### 5-2. 왜 이메일 충돌 시 자동 연결하지 않았는가

이 프로젝트는 이메일 충돌 시 `SocialAccountConflictException`으로 처리합니다.

이유는 간단합니다.

- 이미 존재하는 로컬 계정을 자동 연결하면 위험하다
- 사용자가 정말 같은 사람인지 보장할 수 없다

즉, 편의보다 안전을 택했습니다.

### 5-3. `SocialAccountLinkService`: 연결/해제 정책은 서비스에서 강제한다

[SocialAccountLinkService.java](../src/main/java/com/erp/domain/auth/service/SocialAccountLinkService.java)의 핵심 메서드는 아래입니다.

- `linkSocialAccount(...)`
- `unlinkSocialAccount(...)`

이 서비스는 아래 정책을 강제합니다.

- 이미 다른 계정에 연결된 provider는 link 불가
- 같은 provider의 다른 identity로 교체 불가
- 마지막 로그인 수단이 사라지는 unlink 불가

### 5-4. `MemberSocialAccount`: 소셜 계정을 별도 테이블로 정규화한다

[MemberSocialAccount.java](../src/main/java/com/erp/domain/member/entity/MemberSocialAccount.java)는

- `provider`
- `providerId`
- `unlinkedAt`

을 가집니다.

핵심 메서드는 아래입니다.

- `create(...)`
- `isActive()`
- `unlink()`
- `relink()`

즉, 소셜 계정은 더 이상 `member` 테이블의 부속 컬럼이 아니라
독립적인 도메인 엔티티가 됩니다.

### 5-5. `V8`, `V9`: 스키마도 lifecycle에 맞게 진화한다

- `V8__normalize_member_social_accounts.sql`
  - `member_social_account` 테이블 생성
  - 기존 소셜 데이터 backfill
- `V9__preserve_social_account_history.sql`
  - unlink 이력 보존을 위한 `unlinked_at`

즉, 정책 변경이 코드에서만 끝나지 않고 DB 구조까지 따라갑니다.

## 6. 실제 흐름

```mermaid
sequenceDiagram
    participant User as 사용자
    participant OAuth as OAuth2AuthenticationSuccessHandler
    participant Link as SocialAccountLinkService
    participant Member as MemberSocialAccount

    User->>OAuth: 소셜 로그인 성공
    OAuth->>OAuth: 일반 로그인인지 link intent인지 분기
    OAuth->>Link: linkSocialAccount() 또는 가입/로그인
    Link->>Member: provider binding 저장/검증
```

## 7. 테스트로 검증하기

대표 테스트는 아래입니다.

- `OAuth2AuthenticationSuccessHandlerTest`
  - 이메일 충돌 차단
  - link intent 성공
  - provider replacement 차단
- `MemberApiIntegrationTest`
  - 소셜 연결/해제
  - 로컬 비밀번호 bootstrap

관련 결정 로그도 연속적으로 이어집니다.

- `phase27` 충돌 정책
- `phase28` 명시적 연결
- `phase30` 안전한 해제
- `phase31` 정규화
- `phase32` provider 불변성

즉, 이 기능은 한 번에 완성된 것이 아니라 정책을 점진적으로 다듬은 결과입니다.

> 현재 구현의 선택과 한계
> 이 프로젝트는 같은 provider의 다른 계정으로 교체하는 기능을 의도적으로 막았습니다.
> 사용성보다 계정 식별 안정성과 복구 용이성을 우선한 선택입니다.
> 따라서 실제 계정 교체가 필요하면 사용자 자기 수정이 아니라 운영 정책으로 풀어야 합니다.

## 8. 회고

소셜 로그인은 “붙였다”로 끝나면 생각보다 약합니다.

정말 중요한 질문은 아래입니다.

- 충돌은 어떻게 다루는가?
- 연결과 로그인은 어떻게 구분하는가?
- 마지막 로그인 수단 보호는 어떻게 하는가?
- 같은 provider 교체는 허용하는가?

이 프로젝트는 바로 이 지점을 깊게 다뤘기 때문에
포트폴리오 설명력이 훨씬 좋아졌습니다.

## 9. 취업 포인트

- “소셜 로그인 자체보다 계정 lifecycle 정책을 더 중요하게 봤습니다.”
- “이메일 충돌은 자동 연결하지 않고, 명시적 소셜 연결 플로우를 따로 만들었습니다.”
- “소셜 계정을 별도 테이블로 정규화하고, unlink 이력과 provider 불변성을 보장했습니다.”

### 9-1. 1문장 답변

- “소셜 로그인을 붙인 것이 아니라, 계정 충돌·연결·해제·교체 금지까지 포함한 계정 정책을 설계했습니다.”

### 9-2. 30초 답변

- “소셜 로그인은 편해 보이지만 실제로는 계정 정책이 더 중요합니다. 이 프로젝트는 이메일 충돌 시 자동 병합하지 않고, 로그인과 소셜 연결을 분리했습니다. 소셜 계정은 `member_social_account`로 정규화했고, 마지막 로그인 수단 보호와 같은 provider 교체 금지 정책까지 서비스 계층에서 강제했습니다.”

### 9-3. 예상 꼬리 질문

- “왜 이메일이 같아도 자동 연결하지 않았나요?”
- “왜 소셜 계정을 별도 테이블로 뺐나요?”
- “같은 provider 계정 교체를 막은 이유는 무엇인가요?”

## 10. 시작 상태

- `11`~`17` 글까지 따라와서 기본 로그인, JWT 세션, 보안 정책이 이미 동작해야 합니다.
- Google/Kakao 같은 OAuth provider 설정이 준비돼 있지 않아도 됩니다. 이 글은 먼저 **계정 정책과 lifecycle 설계**를 이해하는 단계입니다.
- 목표는 세 가지입니다.
  - 일반 소셜 로그인과 명시적 계정 연결 분리
  - 소셜 전용 계정의 로컬 비밀번호 bootstrap
  - 같은 provider의 다른 계정으로 교체 금지

## 11. 이번 글에서 바뀌는 파일

```text
- OAuth2 성공 처리 / link intent:
  - src/main/java/com/erp/global/security/oauth2/OAuth2AuthenticationSuccessHandler.java
  - src/main/java/com/erp/global/security/oauth2/OAuth2LinkSessionService.java
- 소셜 계정 정책:
  - src/main/java/com/erp/domain/auth/service/SocialAccountLinkService.java
  - src/main/java/com/erp/domain/member/entity/MemberSocialAccount.java
  - src/main/java/com/erp/domain/member/controller/MemberApiController.java
  - src/main/java/com/erp/domain/auth/controller/SocialAccountLinkController.java
- 뷰:
  - src/main/java/com/erp/domain/auth/controller/AuthViewController.java
  - src/main/resources/templates/auth/settings.html
- 스키마:
  - src/main/resources/db/migration/V8__normalize_member_social_accounts.sql
  - src/main/resources/db/migration/V9__preserve_social_account_history.sql
- 검증:
  - src/test/java/com/erp/global/security/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
  - src/test/java/com/erp/api/MemberApiIntegrationTest.java
- 결정 로그:
  - docs/COMPLETED.md#archive-002
```

## 12. 구현 체크리스트

1. `OAuth2AuthenticationSuccessHandler`에서 일반 로그인과 link intent를 분기합니다.
2. 이메일 충돌 시 자동 병합하지 않고 명시적 오류와 안내 문구를 제공합니다.
3. `MemberSocialAccount`를 별도 테이블로 정규화하고 provider/providerId를 관리합니다.
4. `SocialAccountLinkService`에 연결, 해제, provider 불변성 정책을 넣습니다.
5. 소셜 전용 계정은 settings 화면에서 로컬 비밀번호를 설정할 수 있게 합니다.
6. 테스트로 충돌, 연결, 해제, provider replacement 차단을 검증합니다.

## 13. 실행 / 검증 명령

```bash
./gradlew compileJava compileTestJava
./gradlew --no-daemon fastTest
./gradlew --no-daemon integrationTest
```

코드 흐름만 빠르게 보고 싶다면 아래처럼 관련 테스트만 좁혀 실행할 수 있습니다.

```bash
./gradlew --no-daemon fastTest \
  --tests "com.kinderp.global.security.oauth2.OAuth2AuthenticationSuccessHandlerTest"
./gradlew --no-daemon integrationTest \
  --tests "com.kinderp.api.MemberApiIntegrationTest"
```

다만 블로그의 기본 검증 경로는 전체 `fastTest`, `integrationTest`입니다. 일부 환경에서는 좁힌 `--tests` 실행이 XML result writer 충돌로 실패할 수 있기 때문입니다.

성공하면 확인할 것:

- 소셜 로그인 성공과 소셜 계정 연결이 다른 흐름으로 동작한다
- 기존 이메일과 충돌하면 자동 연결되지 않고 명시적 에러로 처리된다
- 같은 provider의 다른 계정으로 바꾸는 시나리오는 차단된다

## 14. 산출물 체크리스트

- `OAuth2AuthenticationSuccessHandler`가 일반 로그인과 연결 흐름을 구분한다
- `MemberSocialAccount` 테이블과 `V8`, `V9` 마이그레이션이 존재한다
- `SocialAccountLinkService`가 연결 / 해제 / provider 불변성 정책을 담당한다
- `settings.html`에서 소셜 연결 상태와 로컬 비밀번호 bootstrap UI를 볼 수 있다
- `OAuth2AuthenticationSuccessHandlerTest`, `MemberApiIntegrationTest`가 정책을 검증한다

## 15. 글 종료 체크포인트

- 소셜 계정 정보가 `member` 부속 컬럼이 아니라 별도 테이블로 분리돼 있다
- 연결과 로그인 흐름을 구분해 설명할 수 있다
- 마지막 로그인 수단 보호와 provider 불변성 정책을 설명할 수 있다
- settings 화면까지 포함해 lifecycle이 사용자 기능으로 닫혀 있다

## 16. 자주 막히는 지점

- 증상: 소셜 로그인 성공 시 기존 계정에 자동으로 붙어 버린다
  - 원인: 이메일이 같다는 이유만으로 계정을 자동 병합하면 정책이 무너집니다
  - 확인할 것: `OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess(...)`

- 증상: unlink 후 다른 같은 provider 계정으로 다시 연결된다
  - 원인: unlink를 삭제로 처리하거나, 과거 이력을 보지 않을 수 있습니다
  - 확인할 것: `MemberSocialAccount.unlink()`, `SocialAccountLinkService.linkSocialAccount(...)`, `V9__preserve_social_account_history.sql`
