package com.kinderp.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정
 * JPAQueryFactory 빈을 등록합니다.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * JPAQueryFactory 빈 등록
     * QueryDSL을 사용한 타입 안전 쿼리를 작성할 수 있습니다.
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
