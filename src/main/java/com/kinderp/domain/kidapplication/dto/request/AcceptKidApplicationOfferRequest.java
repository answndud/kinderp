package com.kinderp.domain.kidapplication.dto.request;

import com.kinderp.domain.kid.entity.Relationship;

public record AcceptKidApplicationOfferRequest(
        Relationship relationship
) {
    public Relationship relationshipOrDefault() {
        return relationship == null ? Relationship.FATHER : relationship;
    }
}
