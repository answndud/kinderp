package com.kinderp.domain.dashboard.service;

import com.kinderp.domain.dashboard.event.DashboardCacheEvictRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 뒤에 대시보드 통계 캐시를 비운다.
 * 커밋 전에 비우면 그 사이 다른 요청이 아직 커밋되지 않은 옛 데이터로 캐시를 다시 채워 TTL(60초) 동안 낡은 값이 남는다.
 */
@Component
@RequiredArgsConstructor
public class DashboardCacheEvictionListener {

    static final String CACHE_NAME = "dashboardStatistics";

    private final CacheManager cacheManager;

    // 트랜잭션 밖에서 발행된 이벤트도 놓치지 않도록 fallbackExecution을 켠다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void evict(DashboardCacheEvictRequested event) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(event.kindergartenId());
        }
    }
}
