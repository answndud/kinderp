package com.kinderp.domain.dashboard.event;

/**
 * 대시보드 통계 캐시를 비워 달라는 요청. 쓰기 트랜잭션이 커밋된 뒤에 처리된다.
 */
public record DashboardCacheEvictRequested(Long kindergartenId) {
}
