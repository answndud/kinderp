package com.kinderp.domain.kindergarten.repository;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 유치원 리포지토리
 */
@Repository
public interface KindergartenRepository extends JpaRepository<Kindergarten, Long> {

    /**
     * 유치원명으로 조회
     */
    Optional<Kindergarten> findByName(String name);

    /**
     * 전체 유치원 조회
     */
    List<Kindergarten> findAllByOrderByNameAsc();

    /**
     * 유치원명 존재 확인
     */
    boolean existsByName(String name);
}
