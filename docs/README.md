# Docs Index

개발·실행·배포·설계 문서의 인덱스입니다. 현재/향후 구현 작업은 저장소 루트 `TRACK.md`에서 관리합니다.

## Start Here

1. [`TRACK.md`](../TRACK.md): 현재/향후 구현 작업, 검증 명령, 완료 조건
2. [개발자 가이드](guides/developer-guide.md): 구조, 실행, 코드 규칙
3. [환경 계약](guides/env-contract.md): local/demo/prod 환경 변수와 안전한 기본값

## Architecture

- [설계 서사](architecture/portfolio-story.md): 대표 업무 흐름, 권한·상태 전이, Outbox 설계
- [Workflow 상태 전이](architecture/workflow-state-machine.md): 입학 신청·출결 요청·Outbox 상태표와 구현·테스트 위치
- [API 접근 매트릭스](architecture/api-access-matrix.md): 역할별 API 접근 범위
- [성능 측정 방법론](architecture/performance-methodology.md): 측정 조건, 전후 비교, 한계
- [설계 의사결정 기록](architecture/decision-log.md): 주요 기술 선택과 이유

## Guides

- [Netcup 배포](guides/netcup-deployment.md): 단일 VPS 배포·백업·복구 절차
- [Secret 사고 대응](guides/secret-incident-response.md): 키 노출 시 대응 절차
- [시연 시나리오](guides/demo-scenario.md): 역할별 클릭 순서
- [로컬 데모 계정](guides/local-demo-accounts.md): 데모 계정과 시드 데이터
