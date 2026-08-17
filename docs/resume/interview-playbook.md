# Interview Playbook

기준일: 2026-08-14

## 1. 30초 자기소개

저는 Java와 Spring Boot로 복잡한 백엔드 요구사항을 데이터 모델과 API로 구조화하고, 성능·보안·운영 관점에서 검증하는 개발자 지망생입니다. 대표 프로젝트로 Java 21, Spring Boot 3.5.14 기반 KinderP를 만들었고, 단순 CRUD보다 원장/교사/학부모 권한 경계, 유치원 tenant 격리, JWT cookie와 Redis 세션 revoke, 입학 신청 상태 전이, audit log, notification outbox dead-letter 재처리 같은 운영형 문제를 구현했습니다. AI는 문제 분해와 누락 점검에 활용했지만, 최종 구조 선택과 검증은 직접 책임졌습니다.

## 2. 3분 프로젝트 설명

KinderP는 유치원 운영 관리 시스템을 주제로 한 Spring Boot 백엔드 포트폴리오입니다. 원장, 교사, 학부모가 같은 유치원 데이터를 보지만 가능한 액션이 다르기 때문에, 이 프로젝트의 핵심은 CRUD 화면 수가 아니라 권한 경계와 운영 흐름을 안전하게 닫는 것입니다.

첫 번째 축은 인증과 권한입니다. JWT는 HTTP-only cookie로 전달하고, refresh token과 active session revoke는 Redis TTL 기반으로 관리합니다. endpoint 추가 시 URL 권한과 service 권한을 같이 확인하고, 유치원 tenant 경계를 `AccessPolicyService`와 통합 테스트로 검증했습니다.

두 번째 축은 업무 workflow입니다. 입학 신청은 단순 상태 update가 아니라 pending, waitlist, offer, accept 같은 상태 전이를 갖고, 원생 등록, 알림, audit 기록과 연결됩니다. 이 흐름이 너무 커지지 않도록 review 상태 전이와 admission, notification, audit 보조 service를 분리했습니다.

세 번째 축은 운영성입니다. 알림은 외부 provider가 실패할 수 있다는 전제에서 outbox retry/backoff/dead-letter를 만들었고, 원장 전용 운영 화면에서 timeline, status/channel/search 필터, dead-letter retry를 제공합니다. 인증 audit와 업무 audit는 별도 화면과 CSV export를 제공해 운영자가 문제를 조사할 수 있게 했습니다.

마지막으로 검증입니다. Testcontainers 기반 통합 테스트, performance smoke, CI quick/heavy 분리, production-like checklist를 통해 기능이 있다는 주장보다 검증 가능한 증거를 남겼습니다. 성능 면에서는 Notepad query count를 `22 -> 5`, Dashboard cache hit query count를 `5 -> 0`으로 줄였고, CI도 혼자 운영하는 main 프로젝트에 맞게 `5m 28s` 수준에서 1분대 quick check 중심으로 경량화했습니다.

## 3. AI 활용 방식 답변

AI는 세 가지 역할로 사용했습니다.

첫째, 문제 분해 도구로 사용했습니다. "유치원 ERP를 만든다"는 큰 요구를 role/tenant 경계, 세션 revoke, 입학 workflow, outbox 실패 처리, audit 조사, production safety 같은 작은 문제로 나누는 데 활용했습니다.

둘째, 구현 후보를 비교하는 파트너로 사용했습니다. 예를 들어 알림 기능은 실제 provider 연동을 먼저 할 수도 있었지만, 현재 포트폴리오 단계에서는 provider보다 실패를 관측하고 재시도하는 outbox 운영 흐름이 더 강한 증거라고 판단했습니다.

셋째, 검증 gap 탐색에 활용했습니다. AI에게 "이 구조에서 면접관이 공격할 지점"이나 "Spring Boot 운영 관점의 누락"을 찾게 한 뒤, 실제로 코드와 테스트를 읽고 필요한 통합 테스트, performance smoke, risk response 문서로 닫았습니다.

중요한 점은 AI가 제안한 답을 최종 결과로 보지 않았다는 것입니다. 최종 판단은 코드 실행, 테스트, CI, 문서 증거를 기준으로 했습니다.

## 4. 복잡한 요구사항 구조화 답변

복잡한 요구사항을 받으면 먼저 사용자와 권한을 나눕니다. 이 프로젝트에서는 원장, 교사, 학부모가 있고, 같은 유치원 안에서도 볼 수 있는 데이터와 수행 가능한 액션이 다릅니다. 그래서 화면이나 API보다 먼저 role/tenant 경계를 정의했습니다.

그 다음 상태 전이를 분리합니다. 입학 신청, 출석 변경 요청, 알림 outbox처럼 시간이 지나며 상태가 바뀌는 기능은 단순 update로 보면 나중에 audit, 알림, 권한 조건이 섞입니다. 그래서 상태 전이와 side effect를 service 단위로 나누고 테스트 케이스를 성공/실패/권한 실패로 분리했습니다.

마지막으로 운영자가 문제를 볼 수 있는지를 확인합니다. 기능이 성공할 때만 보는 것이 아니라, 실패한 알림을 어떻게 찾고 재시도하는지, 누가 어떤 상태를 바꿨는지 audit으로 볼 수 있는지, prod에서 위험 설정이 켜지지 않는지를 검증했습니다.

## 5. AI 한계와 검증 방식 답변

AI의 한계는 크게 세 가지라고 봅니다. 첫째, 실제 프로젝트 맥락과 다르게 그럴듯한 구조를 제안할 수 있습니다. 둘째, 보안/권한/트랜잭션 같은 실패 비용이 큰 지점을 누락할 수 있습니다. 셋째, 테스트가 없는 상태에서도 "완료"처럼 보이는 답을 만들 수 있습니다.

그래서 저는 AI 결과를 바로 반영하지 않고 저장소 기준으로 확인했습니다. controller 변경은 통합 테스트를 우선했고, 보안 변경은 성공과 실패 케이스를 같이 확인했습니다. 성능 개선은 query count나 elapsed time 중 하나 이상을 숫자로 남겼고, 운영 설정은 startup safety validator와 production-like checklist로 검증했습니다. 문서도 README 주장과 evidence map, 실제 코드/테스트 경로가 서로 맞는지 확인했습니다.

## 6. 예상 공격 질문과 방어 답변

| 질문 | 답변 방향 |
| --- | --- |
| AI가 대부분 해준 것 아닌가요? | AI는 문제 분해, 후보 탐색, 누락 점검에 사용했습니다. 최종 구조 선택, 테스트 통과, 성능 수치, 문서 증거 정리는 제가 책임졌고 코드 경로와 trade-off를 설명할 수 있습니다. |
| 실제 배포가 없는데 운영형이라고 말할 수 있나요? | 실제 운영 중이라고 주장하지 않습니다. 클라우드 비용 제약으로 미배포 상태이며, 대신 Dockerfile, prod compose, env contract, startup safety validator, production-like checklist로 운영 전환 전 리스크를 준비했습니다. |
| 외부 알림 provider는 실제로 붙었나요? | 실제 provider 운영 연동보다 outbox 상태 전이, dead-letter 관측, retry, adapter 교체 경계를 먼저 구현했습니다. provider 계정이 생기면 sandbox smoke, webhook signature, rate limit을 추가하는 순서가 맞습니다. |
| 왜 모놀리식인가요? | 현재 규모에서는 트랜잭션, 권한, tenant 경계를 한 Spring Boot 애플리케이션에서 닫는 것이 더 설명 가능하고 실용적입니다. 분리한다면 notification, audit, reporting 같은 비동기/읽기 영역부터 분리합니다. |
| Frontend asset은 운영 기준으로 관리되나요? | 초기 Tailwind CDN 의존성을 제거하고 저장소 로컬 vendor asset과 Tailwind build 산출물로 전환했습니다. 외부 asset을 추가할 때만 공식 digest/SRI와 bundle size를 별도 검증합니다. |
| full test를 매번 안 돌리면 신뢰도가 낮지 않나요? | push CI는 빠른 실패 신호를 위해 경량화했고, 큰 변경은 manual Backend Quality workflow와 targeted test로 보완했습니다. 혼자 운영하는 main 프로젝트라 검증 비용과 피드백 속도의 균형을 잡았습니다. |
| 실제 고객 요구사항이 아닌데 복잡한 요구사항 처리 경험이라고 볼 수 있나요? | 실제 고객 프로젝트라고 주장하지 않습니다. 다만 role/tenant, state transition, audit, outbox, prod safety처럼 실무 백엔드에서 반복되는 문제를 포트폴리오 도메인에 의도적으로 반영했습니다. |
| QueryDSL/JPA 성능은 어떻게 확인했나요? | README와 performance smoke에 query count/elapsed time 기준을 남겼습니다. 대표적으로 Notepad는 `22 -> 5` queries, Dashboard cache hit은 `5 -> 0` queries로 개선했습니다. |

## 7. 면접 데모 순서

1. [`../../README.md`](../../README.md) 상단의 30초 요약과 핵심 문제 표를 보여줍니다.
2. GitHub Actions 배지와 최신 run history를 보여주고, CI quick/heavy 분리 이유를 설명합니다.
3. `demo` profile로 실행한 화면에서 원장 계정으로 로그인합니다.
4. `/applications/pending`에서 입학 신청 상태 전이와 review workflow를 설명합니다.
5. `/notification-outbox`에서 timeline, status/channel/search 필터, dead-letter retry를 보여줍니다.
6. `/audit-logs`, `/domain-audit-logs`에서 reason/summary 필터와 CSV export를 보여줍니다.
7. [`../guides/evidence-map.md`](../guides/evidence-map.md)를 열어 주장별 코드/테스트 증거를 따라갑니다.
8. 약점 질문이 나오면 [`../guides/risk-response.md`](../guides/risk-response.md)에서 클라우드 미배포와 provider 미연동의 현재 범위·보완 논리를 보여줍니다.

## 8. 마지막에 남길 한 문장

이 프로젝트에서 제가 보여주고 싶은 것은 기능을 빠르게 만드는 데서 멈추지 않고, 요구사항을 서버 구조로 바꾸고 보안·성능·운영 증거까지 닫아가는 개발 방식입니다.
