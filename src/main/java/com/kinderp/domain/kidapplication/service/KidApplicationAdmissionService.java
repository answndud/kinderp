package com.kinderp.domain.kidapplication.service;

import com.kinderp.domain.classroom.entity.Classroom;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.entity.ParentKid;
import com.kinderp.domain.kid.entity.Relationship;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kid.repository.ParentKidRepository;
import com.kinderp.domain.kidapplication.entity.KidApplication;
import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.global.common.ProductTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KidApplicationAdmissionService {

    private final KidRepository kidRepository;
    private final ParentKidRepository parentKidRepository;

    public Kid enrollKid(KidApplication application, Classroom classroom, Relationship relationship) {
        Kid kid = Kid.create(
                classroom,
                application.getKidName(),
                application.getBirthDate(),
                application.getGender(),
                ProductTime.today()
        );
        Kid savedKid = kidRepository.save(kid);

        ParentKid parentKid = ParentKid.create(savedKid, application.getParent(), relationship);
        parentKidRepository.save(parentKid);
        return savedKid;
    }

    public void activateParent(Member parent, Kindergarten kindergarten) {
        if (parent.getKindergarten() == null) {
            parent.assignKindergarten(kindergarten);
        }
        parent.activateMember();
    }
}
