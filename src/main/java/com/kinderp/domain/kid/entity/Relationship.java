package com.kinderp.domain.kid.entity;

import lombok.Getter;

/**
 * 학부모와 원생의 관계 enum
 */
@Getter
public enum Relationship {
    FATHER("아버지"),
    MOTHER("어머니"),
    GRANDFATHER("할아버지"),
    GRANDMOTHER("할머니"),
    GUARDIAN("보호자");

    private final String description;

    Relationship(String description) {
        this.description = description;
    }
}
