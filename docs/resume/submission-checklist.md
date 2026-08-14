# Submission Checklist

기준일: 2026-08-14

## 1. 제출 전 채워야 할 개인정보/링크

| 항목 | 값 |
| --- | --- |
| 이름 | `[입력 필요]` |
| 이메일 | `[입력 필요]` |
| 휴대폰 | `[입력 필요]` |
| GitHub 프로필 | `[입력 필요]` |
| Kindergarten ERP 저장소 URL | `[입력 필요]` |
| 최신 main commit hash | `[입력 필요]` |
| 최신 GitHub Actions 성공 run URL | `[입력 필요]` |
| README 스크린샷 최신화 여부 | `[확인 필요]` |
| 2분 이하 데모 영상 URL | `[선택 입력]` |
| 이력서/Notion/포트폴리오 URL | `[선택 입력]` |
| 실제 클라우드 배포 여부 | `미배포: 비용 제약으로 production-like 검증까지만 수행` |

## 2. 금지 표현과 대체 표현

| 금지 표현 | 이유 | 대체 표현 |
| --- | --- | --- |
| "실제 운영 중인 ERP 서비스" | 현재 클라우드 미배포 | "운영형 백엔드 문제를 다룬 포트폴리오 프로젝트" |
| "배포 완료" | 실제 서버 smoke 없음 | "Docker/prod compose/env contract와 production-like checklist 준비" |
| "AI가 개발했습니다" | 책임 소재가 약해짐 | "AI를 문제 분해, 구현 후보 탐색, 검증 gap 점검에 활용했고 최종 검증은 직접 수행" |
| "완벽한 보안" | 과장 | "role/tenant 경계, HTTP-only cookie JWT, Redis session revoke, prod fail-closed 설정을 구현하고 테스트로 검증" |
| "실제 외부 알림 발송 운영" | provider 운영 검증 아님 | "provider adapter 경계와 outbox retry/dead-letter 운영 흐름 구현" |
| "풀스택 완성 서비스" | frontend build pipeline은 제한적 | "백엔드 중심 프로젝트이며 Thymeleaf/HTMX 기반 SSR 운영 화면 포함" |
| "테스트 완벽" | 모든 경우를 보장할 수 없음 | "핵심 API/보안/성능/운영 설정에 targeted test와 smoke test를 보강" |
| "대규모 트래픽 대응" | 실제 부하 검증 없음 | "N+1 제거와 핵심 조회 query count 개선을 performance smoke로 검증" |

## 3. 최종 점검 체크리스트

- [ ] `application-draft.md`의 `[이름]`, `[GitHub URL]`, `[연락처]`, `[최신 CI URL]` 값을 채웠다.
- [ ] README 상단 프로젝트 설명이 현재 상태와 맞고, "운영 중"처럼 과장된 표현이 없다.
- [ ] GitHub Actions 최신 main run이 성공 상태이며 제출 링크에 반영했다.
- [ ] README의 대표 성능 수치가 현재 문서와 충돌하지 않는다.
- [ ] 클라우드 미배포 사실을 숨기지 않고 production-like 준비 상태로 설명한다.
- [ ] 외부 notification provider를 실제 운영했다고 표현하지 않는다.
- [ ] 면접에서 열 증거 문서 순서를 연습했다: README -> evidence map -> interview guide -> risk response -> demo scenario.
- [ ] 데모를 할 경우 `demo` profile 계정이 로그인되는지 사전에 확인했다.
- [ ] `/applications/pending`, `/notification-outbox`, `/audit-logs`, `/domain-audit-logs` 화면이 비어 있지 않은지 확인했다.
- [ ] 최신 commit hash와 제출 시점 브랜치가 `main`인지 확인했다.
- [ ] 제출 문장에 "AI를 썼다"만 있고 검증 책임 설명이 빠진 부분이 없는지 확인했다.
- [ ] 약점 질문에 대해 "현재 상태", "왜 이렇게 했는지", "운영 전 보완책" 순서로 답할 수 있다.

## 4. 제출 직전 5분 점검 루트

1. GitHub 저장소 README를 열어 30초 요약, 핵심 문제, 성능 수치, CI 배지를 확인합니다.
2. 최신 GitHub Actions main run이 성공인지 확인합니다.
3. [`strategy.md`](strategy.md)의 핵심 사례 3개를 한 문장씩 말해봅니다.
4. [`interview-playbook.md`](interview-playbook.md)의 공격 질문 중 "AI가 다 한 것 아닌가요?", "배포 안 했는데 운영형인가요?", "외부 알림 실제 발송인가요?" 답변을 확인합니다.
5. [`application-draft.md`](application-draft.md)의 짧은 제출 버전만 복사해도 문맥이 자연스러운지 확인합니다.
