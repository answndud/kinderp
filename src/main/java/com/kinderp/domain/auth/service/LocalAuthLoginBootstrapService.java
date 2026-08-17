package com.kinderp.domain.auth.service;

import com.kinderp.domain.kindergarten.entity.Kindergarten;
import com.kinderp.domain.kindergarten.repository.KindergartenRepository;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Primary
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LocalAuthLoginBootstrapService implements AuthLoginBootstrapService {

    private static final String DEFAULT_KINDERGARTEN_NAME = "해바라기 유치원";

    private final MemberRepository memberRepository;
    private final KindergartenRepository kindergartenRepository;

    @Override
    @Transactional
    public void afterAuthenticated(String email) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) {
            return;
        }

        if (member.getRole() != MemberRole.PRINCIPAL || member.getKindergarten() != null) {
            return;
        }

        Kindergarten target = kindergartenRepository.findByName(DEFAULT_KINDERGARTEN_NAME)
                .orElseGet(() -> kindergartenRepository.findAllByOrderByNameAsc().stream().findFirst().orElse(null));

        if (target == null) {
            log.warn("[local] No kindergarten found for principal auto-assignment: {}", email);
            return;
        }

        member.assignKindergarten(target);
        log.info("[local] Auto-assigned principal {} to kindergarten {}", email, target.getName());
    }
}
