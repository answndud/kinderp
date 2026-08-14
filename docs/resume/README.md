# 토스뱅크 Server Developer Resume Package

기준일: 2026-08-14

이 폴더는 토스뱅크 Server Developer 채용 연계형 인턴십 지원을 위해 Kindergarten ERP 프로젝트를 포트폴리오로 설명하는 제출 패키지입니다. 목적은 프로젝트를 과장해서 포장하는 것이 아니라, 공고가 보는 평가 기준과 저장소의 실제 증거를 1:1로 연결하는 것입니다.

## 읽는 순서

1. [`strategy.md`](strategy.md): 공고 평가 기준, 프로젝트 강점 매핑, 약점 방어 논리를 먼저 확인합니다.
2. [`application-draft.md`](application-draft.md): 실제 지원서에 넣을 문장을 상황에 맞게 줄이거나 조정합니다.
3. [`interview-playbook.md`](interview-playbook.md): 면접에서 프로젝트를 설명하고 공격 질문에 답하는 순서를 연습합니다.
4. [`submission-checklist.md`](submission-checklist.md): 제출 전 개인정보, 링크, 금지 표현, 최종 점검 항목을 채웁니다.

## 핵심 포지셔닝

> 복잡한 운영형 백엔드 요구사항을 문제로 재정의하고, Java/Spring으로 구현한 뒤 테스트·성능·운영 문서로 최종 검증까지 책임지는 서버 개발자.

Kindergarten ERP는 유치원 운영을 주제로 한 Spring Boot 백엔드 포트폴리오입니다. 단순 CRUD보다 원장/교사/학부모 역할 경계, 유치원 tenant 경계, 입학 신청 상태 전이, 감사 로그, outbox dead-letter 재처리, production-like 안전장치, CI 경량화 같은 운영형 문제를 끝까지 닫는 데 초점을 둡니다.

## 제출에서 사용할 주요 증거 링크

| 주장 | 증거 |
| --- | --- |
| 단순 CRUD가 아니라 운영형 백엔드 문제를 풀었다 | [`../../README.md`](../../README.md), [`../guides/evidence-map.md`](../guides/evidence-map.md) |
| role/tenant 보안 경계를 API/service/test에서 검증했다 | [`../../src/main/java/com/erp/global/security/access`](../../src/main/java/com/erp/global/security/access), [`../../src/test/java/com/erp/api`](../../src/test/java/com/erp/api) |
| JWT cookie + Redis refresh/session revoke를 구현했다 | [`../../src/main/java/com/erp/global/security/jwt`](../../src/main/java/com/erp/global/security/jwt), [`../../src/main/java/com/erp/domain/auth`](../../src/main/java/com/erp/domain/auth) |
| 알림 실패를 outbox timeline/dead-letter/retry로 운영 가능하게 만들었다 | [`../../src/main/java/com/erp/domain/notification`](../../src/main/java/com/erp/domain/notification), [`../../src/main/resources/templates/notifications/outbox.html`](../../src/main/resources/templates/notifications/outbox.html) |
| 감사 로그와 CSV export로 운영 조사 흐름을 제공한다 | [`../../src/main/java/com/erp/domain/authaudit`](../../src/main/java/com/erp/domain/authaudit), [`../../src/main/java/com/erp/domain/domainaudit`](../../src/main/java/com/erp/domain/domainaudit) |
| 성능 개선을 숫자로 남겼다 | [`../../README.md`](../../README.md), [`../../src/test/java/com/erp/performance`](../../src/test/java/com/erp/performance) |
| 실제 클라우드 미배포 약점을 숨기지 않고 production-like 검증으로 방어한다 | [`../guides/risk-response.md`](../guides/risk-response.md), [`../guides/production-like-checklist.md`](../guides/production-like-checklist.md), [`../../deploy`](../../deploy) |
| 면접 시연 흐름이 준비되어 있다 | [`../guides/interview-guide.md`](../guides/interview-guide.md), [`../guides/demo-scenario.md`](../guides/demo-scenario.md) |
| 현재 작업 범위와 검증 근거를 추적한다 | [`../../PLAN.md`](../../PLAN.md), [`../guides/evidence-map.md`](../guides/evidence-map.md) |

## 사용 원칙

- 제출 문장에는 "운영 중"이라고 쓰지 않습니다. 현재 상태는 클라우드 미배포이며, production-like 배포 자산과 dry-run 검증을 준비한 상태입니다.
- "AI가 개발했다"가 아니라 "AI를 문제 분해, 구현 초안, 검증 후보 탐색에 활용했고 최종 판단과 검증은 직접 책임졌다"로 표현합니다.
- 수치가 필요한 곳에는 README와 테스트/CI 근거가 있는 값만 사용합니다.
- 실제 제출 전에는 [`submission-checklist.md`](submission-checklist.md)의 개인정보와 최신 GitHub Actions 링크를 반드시 채웁니다.
