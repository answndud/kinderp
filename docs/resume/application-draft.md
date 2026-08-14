# Application Draft

기준일: 2026-08-14

이 문서는 토스뱅크 Server Developer 채용 연계형 인턴십 제출용 초안입니다. 실제 제출 전 `[이름]`, `[GitHub URL]`, `[연락처]`, `[최신 CI URL]` 같은 값을 채워야 합니다.

## 자기소개

저는 복잡한 요구사항을 작은 기술 문제로 나누고, AI를 활용해 구현 속도를 높이되 최종 검증 책임은 직접 지는 백엔드 개발자 지망생입니다. 최근에는 Spring Boot 기반 Kindergarten ERP 프로젝트를 통해 단순 CRUD가 아니라 role/tenant 권한 경계, JWT cookie와 Redis 기반 세션 revoke, 입학 신청 상태 전이, 감사 로그, notification outbox dead-letter 재처리, production-like 안전장치까지 포함한 운영형 백엔드 흐름을 구현했습니다.

제가 중요하게 보는 기준은 "작동한다"에서 끝내지 않는 것입니다. AI가 제안한 구조나 코드를 그대로 믿지 않고, 통합 테스트, 성능 smoke, CI, 문서화로 검증 가능한 상태까지 닫는 것을 목표로 작업했습니다. 이 프로젝트에서도 Notepad 조회 query count를 `22 -> 5`, Dashboard cache hit query count를 `5 -> 0`으로 줄였고, 혼자 운영하는 main 프로젝트에 맞게 CI를 quick check와 manual quality workflow로 분리했습니다.

## 지원 이유

토스뱅크 Server Developer 인턴십에 지원하는 이유는 복잡한 서비스를 빠르고 안정적인 구조로 만들기 위해 비즈니스 요구사항을 데이터 모델과 API로 구체화하고, 성능·자동화·운영까지 연결하는 경험을 쌓고 싶기 때문입니다. 저는 AI를 단순히 코드를 대신 쓰는 도구로 보지 않습니다. 요구사항을 다시 정의하고, 설계 후보를 비교하고, 테스트 누락 지점을 찾고, 문서를 정리하는 보조 수단으로 사용합니다. 다만 최종 결과가 맞는지 판단하고 책임지는 일은 개발자의 몫이라고 생각합니다.

Kindergarten ERP는 이 관점을 보여주기 위한 프로젝트입니다. 실제 클라우드 배포는 비용 문제로 진행하지 않았지만, 그 약점을 숨기지 않고 Dockerfile, prod compose, env contract, startup safety validator, production-like checklist로 운영 전환 전 확인해야 할 항목을 정리했습니다. 특히 역할·tenant 경계, 상태 전이, idempotency, outbox 재처리, MySQL 조회 성능을 구현하고 테스트로 검증했습니다.

## AI 활용 경험

AI는 이 프로젝트에서 네 가지 방식으로 활용했습니다.

1. 요구사항 분해: 유치원 ERP를 단순 게시판/회원 CRUD로 만들지 않고, 원장/교사/학부모의 권한 경계, tenant 격리, 상태 전이, 감사, 알림 실패 대응 같은 백엔드 문제로 다시 나누는 데 활용했습니다.
2. 구현 후보 탐색: 인증/세션, outbox, audit, 성능 개선, CI 경량화처럼 여러 선택지가 있는 작업에서 AI로 후보를 빠르게 정리하고, 현재 프로젝트 규모에 맞는 최소 충분한 선택을 골랐습니다.
3. 검증 gap 탐색: 컨트롤러 입력 오류가 500으로 떨어질 수 있는 지점, outbox 운영 API의 권한 실패 케이스, audit 필터/export 정합성, production profile의 위험 설정 같은 누락 가능성을 점검했습니다.
4. 설명 가능성 강화: README, evidence map, risk response, interview guide, demo scenario, DONE archive를 정리해 면접관이나 협업자가 주장과 코드 증거를 따라갈 수 있게 만들었습니다.

AI를 사용했지만, 최종 기준은 항상 저장소의 실제 결과였습니다. Spring Boot 통합 테스트, Testcontainers 기반 검증, performance smoke, GitHub Actions, `git diff --check`, production-like checklist를 통해 AI가 만든 초안과 제 판단을 다시 확인했습니다.

## 포트폴리오 설명

대표 포트폴리오는 Kindergarten ERP입니다.

- 저장소: `[GitHub URL 입력]`
- 주요 기술: Java 21, Spring Boot 3.5.9, JPA, QueryDSL, Spring Security, JWT, Redis, MySQL 8, Flyway, Thymeleaf, HTMX, Alpine.js
- 핵심 문제: 유치원 운영에서 역할별 권한, 유치원 tenant 경계, 입학 신청 workflow, 출석/알림/audit 운영 흐름을 안전하게 처리하는 것
- 운영 관점: HTTP-only cookie JWT, Redis refresh/session revoke, audit log, notification outbox retry/dead-letter, Prometheus/Grafana, readiness, startup safety validator
- 검증 관점: API 통합 테스트, Spring Security 테스트, Testcontainers, performance smoke, CI quick/heavy 분리, production-like dry-run checklist
- 성능 개선: Notepad `22 -> 5` queries, Dashboard cache hit `5 -> 0` queries, Backend CI `5m 28s -> 1분대`

이 프로젝트는 실제 클라우드 운영 서비스라고 주장하지 않습니다. 대신 신입 백엔드 개발자가 운영에서 문제가 되는 경계와 실패 상황을 어디까지 상상하고 검증했는지 보여주는 포트폴리오입니다. 특히 outbox timeline/dead-letter 재처리, audit log 필터/export, fail-closed production 설정은 "기능 구현"보다 "운영 중 문제를 어떻게 볼 것인가"에 초점을 맞춘 부분입니다.

## 짧은 제출 버전

저는 AI를 코드 생성 도구로만 쓰지 않고, 요구사항 분해와 검증 gap 탐색에 적극적으로 활용하는 백엔드 개발자 지망생입니다. 대표 포트폴리오인 Kindergarten ERP에서는 유치원 운영 도메인을 단순 CRUD가 아니라 role/tenant 권한 경계, JWT cookie + Redis 세션 revoke, 입학 신청 상태 전이, audit log, notification outbox dead-letter 재처리, production-like 안전장치 문제로 재구성해 구현했습니다.

이 프로젝트에서 Notepad 조회 query count를 `22 -> 5`, Dashboard cache hit query count를 `5 -> 0`으로 줄였고, Testcontainers 통합 테스트, performance smoke, GitHub Actions, production-like checklist로 검증 가능한 결과를 남겼습니다. 실제 클라우드 배포는 비용 문제로 아직 하지 않았지만, Dockerfile, prod compose, env contract, startup safety validator를 통해 운영 전환 시 확인해야 할 리스크를 문서화했습니다.

토스뱅크에서는 새로운 금융 도메인을 빠르게 학습해 요구사항을 데이터 모델과 API로 설계하고, 확장성·가용성·성능을 고려한 서버를 만드는 개발자로 성장하고 싶습니다. 이를 위해 현재도 문제 정의부터 구현, 측정, 운영 문서화까지 한 사이클로 닫는 연습을 이어가고 있습니다.

## 아주 짧은 소개 문장

AI를 활용해 복잡한 백엔드 요구사항을 빠르게 구조화하고, 테스트/성능/운영 문서로 최종 검증까지 책임지는 Java Spring Boot 개발자 지망생입니다.
