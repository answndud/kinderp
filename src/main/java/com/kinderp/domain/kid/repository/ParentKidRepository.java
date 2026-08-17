package com.kinderp.domain.kid.repository;

import com.kinderp.domain.kid.entity.ParentKid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParentKidRepository extends JpaRepository<ParentKid, Long> {

    List<ParentKid> findByKidId(Long kidId);

    List<ParentKid> findByParentId(Long parentId);

    @Query("SELECT DISTINCT pk.parent.id FROM ParentKid pk WHERE pk.kid.id IN :kidIds")
    List<Long> findDistinctParentIdsByKidIds(@Param("kidIds") List<Long> kidIds);

    void deleteByKidId(Long kidId);

    void deleteByParentId(Long parentId);
}
