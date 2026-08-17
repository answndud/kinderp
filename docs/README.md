# Docs Index

이 폴더는 개발, 실행, 환경, 배포 가이드를 모아 둡니다.
현재/향후 구현 작업은 저장소 루트 `PLAN.md`에서 관리합니다.

취업 포트폴리오용 한글 문서의 시작점은 [`docs/문서-안내.md`](문서-안내.md)입니다. 제품 요구사항, 시스템 설계, 기능 카탈로그, 성능·보안·운영 증거, 면접 질문, TownPet과의 차별화 자료를 번호순으로 읽을 수 있습니다.

## Start Here

새 세션에서 먼저 읽는 순서는 아래와 같습니다.

1. `PLAN.md`
2. `docs/guides/developer-guide.md`
3. `docs/guides/env-contract.md`

## Project State

- `PLAN.md`
  - 현재/향후 구현 작업, 검증 명령, 완료 조건

## Guides

- `docs/guides/developer-guide.md`
  - 개발/구조/검증/문서화 기준
- `docs/guides/env-contract.md`
  - local/demo/prod 실행 환경 변수와 안전한 기본값 계약
- `docs/guides/user-guide.md`
  - 역할별 사용 흐름
- `docs/guides/deployment-guide.md`
  - AWS EC2+RDS 대안과 기존 운영 자산 설명(현재 실행 SSOT 아님)
- `docs/guides/netcup-deployment.md`
  - TownPet과 공유하는 netcup VPS Lite 2 단일 VPS 배포 절차
- `docs/guides/interview-guide.md`
  - 면접관 관점의 핵심 개선 스토리와 검증 포인트
- `docs/guides/evidence-map.md`
  - README/면접 답변의 주요 주장별 코드, 테스트, 문서 증거 지도
- `docs/guides/risk-response.md`
  - 미배포, CDN, 모놀리식, demo/mock 범위 등 약점 질문과 운영 전 보완책
- `docs/guides/production-like-checklist.md`
  - 실제 클라우드 배포 없이 반복 가능한 prod safety, bootJar, compose config dry-run checklist
- `docs/guides/demo-scenario.md`
  - demo 실행, 계정, 5분/10분 시연 순서와 실패 시 복구 절차
- `docs/guides/local-demo-accounts.md`
  - 로컬 데모 계정 전체 목록, 시드 데이터, 역할별 테스트 순서와 초기화 방법

## Architecture

- `docs/architecture/rework-history-2026-08-14.md`
  - 이번 개편 작업의 기능 추가·수정·삭제·검증·잔여 범위 상세 기록
- `docs/architecture/portfolio-story.md`
  - TownPet과의 차별화, 대표 업무 흐름, 설계 결정, 면접 시연 순서, 현재 증거와 남은 보완점
- `docs/architecture/workflow-state-machine.md`
  - 출결·입학·Outbox의 허용 상태 전이, 동시성·tenant scope 증거
- `docs/architecture/performance-methodology.md`
  - JVM query-count story와 k6 HTTP p95 측정 조건 및 한계

## Rules

- 현재/향후 작업은 루트 `PLAN.md`에만 남깁니다.
- 완료된 작업은 `PLAN.md`에서 제거하고 별도 완료 로그를 만들지 않습니다.
- `docs/COMPLETED.md`는 기존 블로그 링크와 역사적 결정 기록을 위한 archive이며 active SSOT로 취급하지 않습니다.
- 블로그 작업 SSOT는 별도로 루트의 `BLOG_PLAN.md`, `BLOG_PROGRESS.md`를 사용합니다.
