package com.erp.domain.kindergarten.entity;

import com.erp.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 유치원 엔티티
 */
@Entity
@Table(name = "kindergarten")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Kindergarten extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 유치원명
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 주소
     */
    @Column(name = "address")
    private String address;

    /**
     * 전화번호
     */
    @Column(name = "phone")
    private String phone;

    /**
     * 오픈 시간
     */
    @Column(name = "open_time")
    private LocalTime openTime;

    /**
     * 종료 시간
     */
    @Column(name = "close_time")
    private LocalTime closeTime;

    /**
     * 유치원 생성
     */
    public static Kindergarten create(String name, String address, String phone,
                                       LocalTime openTime, LocalTime closeTime) {
        Kindergarten kindergarten = new Kindergarten();
        kindergarten.name = name;
        kindergarten.address = address;
        kindergarten.phone = phone;
        kindergarten.openTime = openTime;
        kindergarten.closeTime = closeTime;
        return kindergarten;
    }

    /**
     * 유치원 정보 수정
     */
    public void update(String name, String address, String phone,
                       LocalTime openTime, LocalTime closeTime) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

}
