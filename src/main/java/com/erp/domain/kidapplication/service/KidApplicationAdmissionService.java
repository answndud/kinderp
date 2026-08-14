package com.erp.domain.kidapplication.service;

import com.erp.domain.classroom.entity.Classroom;
import com.erp.domain.kid.entity.Kid;
import com.erp.domain.kid.entity.ParentKid;
import com.erp.domain.kid.entity.Relationship;
import com.erp.domain.kid.repository.KidRepository;
import com.erp.domain.kid.repository.ParentKidRepository;
import com.erp.domain.kidapplication.entity.KidApplication;
import com.erp.domain.kindergarten.entity.Kindergarten;
import com.erp.domain.member.entity.Member;
import com.erp.global.common.ProductTime;
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
