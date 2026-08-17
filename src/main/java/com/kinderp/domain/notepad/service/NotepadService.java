package com.kinderp.domain.notepad.service;

import com.kinderp.domain.classroom.service.ClassroomService;
import com.kinderp.domain.kid.entity.Kid;
import com.kinderp.domain.kid.entity.ParentKid;
import com.kinderp.domain.kid.repository.ParentKidRepository;
import com.kinderp.domain.kid.repository.KidRepository;
import com.kinderp.domain.kid.service.KidService;
import com.kinderp.domain.member.entity.Member;
import com.kinderp.domain.member.entity.MemberRole;
import com.kinderp.domain.member.service.MemberService;
import com.kinderp.domain.notepad.dto.request.NotepadRequest;
import com.kinderp.domain.notepad.dto.response.NotepadDetailResponse;
import com.kinderp.domain.notepad.dto.response.NotepadResponse;
import com.kinderp.domain.notepad.entity.Notepad;
import com.kinderp.domain.notepad.entity.NotepadReadConfirm;
import com.kinderp.domain.notepad.repository.NotepadRepository;
import com.kinderp.domain.notification.entity.NotificationType;
import com.kinderp.domain.notification.service.NotificationService;
import com.kinderp.global.exception.BusinessException;
import com.kinderp.global.exception.ErrorCode;
import com.kinderp.global.security.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알림장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotepadService {

    private final NotepadRepository notepadRepository;
    private final ClassroomService classroomService;
    private final KidService kidService;
    private final KidRepository kidRepository;
    private final ParentKidRepository parentKidRepository;
    private final MemberService memberService;
    private final NotificationService notificationService;
    private final AccessPolicyService accessPolicyService;

    /**
     * 반별 알림장 생성
     */
    @Transactional
    public Long createClassroomNotepad(Long classroomId, Long writerId, String title, String content) {
        // 반 조회
        var classroom = classroomService.getClassroom(classroomId);
        // 작성자 조회
        Member writer = memberService.getMemberById(writerId);

        // 교사/원장 역할 확인
        validateWriterRole(writer);
        accessPolicyService.validateClassroomManageAccess(writer, classroom);

        Notepad notepad = Notepad.createClassroomNotepad(classroom, writer, title, content);
        Notepad saved = notepadRepository.save(notepad);
        notifyParentsAboutNotepad(saved, writer);
        return saved.getId();

    }

    /**
     * 전체 알림장 생성
     */
    @Transactional
    public Long createGlobalNotepad(Long writerId, String title, String content) {
        // 작성자 조회
        Member writer = accessPolicyService.getRequester(writerId);

        // 원장 역할 확인
        if (writer.getRole() != MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Notepad notepad = Notepad.createGlobalNotepad(writer, title, content);
        Notepad saved = notepadRepository.save(notepad);
        notifyParentsAboutNotepad(saved, writer);
        return saved.getId();
    }

    /**
     * 알림장 생성 (요청 DTO 사용)
     */
    @Transactional
    public Long createNotepad(NotepadRequest request, Long writerId) {
        log.debug("알림장 생성 요청 - classroomId: {}, kidId: {}, title: {}",
                  request.getClassroomId(), request.getKidId(), request.getTitle());

        Member writer = accessPolicyService.getRequester(writerId);
        validateWriterRole(writer);

        Notepad notepad;

        if (request.getKidId() != null) {
            // 원생별 알림장
            log.debug("원생별 알림장 생성 - kidId: {}", request.getKidId());
            var kid = kidService.getKid(request.getKidId());
            accessPolicyService.validateKidManageAccess(writer, kid);
            notepad = Notepad.createKidNotepad(kid, writer, request.getTitle(), request.getContent());
        } else if (request.getClassroomId() != null) {
            // 반별 알림장
            log.debug("반별 알림장 생성 - classroomId: {}", request.getClassroomId());
            var classroom = classroomService.getClassroom(request.getClassroomId());
            accessPolicyService.validateClassroomManageAccess(writer, classroom);
            notepad = Notepad.createClassroomNotepad(classroom, writer, request.getTitle(), request.getContent());
        } else {
            // 전체 알림장
            log.debug("전체 알림장 생성");
            if (writer.getRole() != com.kinderp.domain.member.entity.MemberRole.PRINCIPAL) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            notepad = Notepad.createGlobalNotepad(writer, request.getTitle(), request.getContent());
        }

        // 사진 URL 설정
        if (request.getPhotoUrl() != null) {
            notepad.setPhotoUrls(request.getPhotoUrl());
        }

        Notepad saved = notepadRepository.save(notepad);
        notifyParentsAboutNotepad(saved, writer);
        return saved.getId();
    }

    private void notifyParentsAboutNotepad(Notepad notepad, Member writer) {
        if (notepad == null || writer == null) {
            return;
        }

        String title = "알림장: " + notepad.getTitle();
        String content = notepad.getContent();
        String linkUrl = "/notepad/" + notepad.getId();

        if (notepad.isKidNotepad() && notepad.getKid() != null) {
            List<ParentKid> parentKids = kidRepository.findParentsByKidId(notepad.getKid().getId());
            List<Long> receiverIds = parentKids.stream()
                    .map(pk -> pk.getParent().getId())
                    .distinct()
                    .toList();
            notificationService.notifyWithLink(receiverIds, NotificationType.NOTEPAD_CREATED, title, content, linkUrl);
            return;
        }

        if (notepad.isClassroomNotepad() && notepad.getClassroom() != null) {
            Long classroomId = notepad.getClassroom().getId();
            List<Kid> kids = kidRepository.findByClassroomIdAndDeletedAtIsNull(classroomId);
            List<Long> kidIds = kids.stream()
                    .map(Kid::getId)
                    .toList();

            if (kidIds.isEmpty()) {
                return;
            }

            Set<Long> receiverIds = parentKidRepository.findDistinctParentIdsByKidIds(kidIds).stream()
                    .collect(Collectors.toSet());
            notificationService.notifyWithLink(receiverIds.stream().toList(), NotificationType.NOTEPAD_CREATED, title, content, linkUrl);
            return;
        }

        if (notepad.isGlobalNotepad()) {
            Long kindergartenId = writer.getKindergarten() != null ? writer.getKindergarten().getId() : null;
            if (kindergartenId == null) {
                return;
            }
            List<Member> parents = memberService.getMembersByKindergartenAndRoles(
                    kindergartenId,
                    List.of(MemberRole.PARENT)
            );
            if (!parents.isEmpty()) {
                List<Long> receiverIds = new java.util.ArrayList<>();
                for (Member parent : parents) {
                    receiverIds.add(parent.getId());
                }
                notificationService.notifyWithLink(receiverIds, NotificationType.NOTEPAD_CREATED, title, content, linkUrl);
            }
        }
    }

    private Notepad getNotepad(Long id) {
        return notepadRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTEPAD_NOT_FOUND));
    }

    public Notepad getNotepad(Long id, Long requesterId) {
        Notepad notepad = getNotepad(id);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateNotepadReadAccess(requester, notepad);
        return notepad;
    }

    /**
     * 알림장 상세 조회 (읽음 확인 포함)
     */
    public NotepadDetailResponse getNotepadDetail(Long id, Long readerId) {
        Notepad notepad = getNotepad(id);
        if (readerId != null) {
            Member requester = accessPolicyService.getRequester(readerId);
            accessPolicyService.validateNotepadReadAccess(requester, notepad);
        }
        List<NotepadReadConfirm> readConfirms = notepadRepository.findReadConfirmsByNotepadId(id);

        // 읽음 확인
        boolean isRead = false;
        if (readerId != null) {
            isRead = readConfirms.stream()
                    .anyMatch(rc -> rc.getReader().getId().equals(readerId));
        }

        return NotepadDetailResponse.from(notepad, readConfirms, isRead);
    }

    public Page<NotepadResponse> getNotepadsByKindergarten(Long kindergartenId, Pageable pageable, Long requesterId) {
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateSameKindergarten(requester, kindergartenId);
        return mapWithReadCounts(notepadRepository.findByKindergartenId(kindergartenId, pageable));
    }

    public Page<NotepadResponse> getClassroomNotepads(Long classroomId, Pageable pageable, Long requesterId) {
        var classroom = classroomService.getClassroom(classroomId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateClassroomReadAccess(requester, classroom);
        return mapWithReadCounts(notepadRepository.findClassroomNotepads(classroomId, pageable));
    }

    public Page<NotepadResponse> getKidNotepads(Long kidId, Pageable pageable, Long requesterId) {
        var kid = kidService.getKid(kidId);
        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateKidReadAccess(requester, kid);
        return mapWithReadCounts(notepadRepository.findKidNotepads(kidId, pageable));
    }

    public Page<NotepadResponse> getNotepadsForParent(Long classroomId, Long kidId, Pageable pageable, Long parentId) {
        Member parent = accessPolicyService.getRequester(parentId);
        if (parent.getRole() != MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        var kid = kidService.getKid(kidId);
        accessPolicyService.validateKidReadAccess(parent, kid);

        if (kid.getClassroom() == null || !kid.getClassroom().getId().equals(classroomId)) {
            throw new BusinessException(ErrorCode.NOTEPAD_ACCESS_DENIED);
        }

        return mapWithReadCounts(notepadRepository.findNotepadsForParent(classroomId, kidId, pageable));
    }

    /**
     * 학부모용 알림장 목록 (내 원생 전체 기준)
     */
    public Page<NotepadResponse> getNotepadsForParent(Long parentId, Pageable pageable) {
        var kids = kidService.getKidsByParent(parentId);

        if (kids.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> kidIds = kids.stream()
                .map(k -> k.getId())
                .distinct()
                .toList();

        List<Long> classroomIds = kids.stream()
                .map(k -> k.getClassroom().getId())
                .distinct()
                .toList();

        return mapWithReadCounts(notepadRepository.findNotepadsForParentKids(classroomIds, kidIds, pageable));
    }


    /**
     * 알림장 수정
     */
    @Transactional
    public void updateNotepad(Long id, NotepadRequest request, Long requesterId) {
        Notepad notepad = getNotepad(id);

        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateNotepadManageAccess(requester, notepad);

        notepad.update(request.getTitle(), request.getContent());

        if (request.getPhotoUrl() != null) {
            notepad.setPhotoUrls(request.getPhotoUrl());
        }
    }

    /**
     * 알림장 삭제
     */
    @Transactional
    public void deleteNotepad(Long id, Long requesterId) {
        Notepad notepad = getNotepad(id);

        Member requester = accessPolicyService.getRequester(requesterId);
        accessPolicyService.validateNotepadManageAccess(requester, notepad);

        notepadRepository.delete(notepad);
    }

    /**
     * 알림장 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notepadId, Long readerId) {
        Notepad notepad = getNotepad(notepadId);
        Member reader = accessPolicyService.getRequester(readerId);

        // 학부모 역할 확인
        if (reader.getRole() != com.kinderp.domain.member.entity.MemberRole.PARENT) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        accessPolicyService.validateNotepadReadAccess(reader, notepad);

        // 이미 읽음 확인이 있는지 확인
        var existingConfirm = notepadRepository.findByNotepadIdAndReaderId(notepadId, readerId);

        if (existingConfirm.isEmpty()) {
            notepad.addReadConfirm(reader);
        } else {
            existingConfirm.get().updateReadTime();
        }
    }

    /**
     * 작성자 역할 확인 (교사 또는 원장)
     */
    private void validateWriterRole(Member writer) {
        if (writer.getRole() != com.kinderp.domain.member.entity.MemberRole.TEACHER &&
            writer.getRole() != com.kinderp.domain.member.entity.MemberRole.PRINCIPAL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 알림장 Response 변환
     */
    public NotepadResponse toResponse(Notepad notepad) {
        int readCount = loadReadCountMap(List.of(notepad.getId())).getOrDefault(notepad.getId(), 0);
        return NotepadResponse.from(notepad, readCount);
    }

    private Page<NotepadResponse> mapWithReadCounts(Page<Notepad> notepads) {
        List<Long> notepadIds = notepads.getContent().stream()
                .map(Notepad::getId)
                .toList();
        Map<Long, Integer> readCountMap = loadReadCountMap(notepadIds);

        return notepads.map(notepad -> NotepadResponse.from(
                notepad,
                readCountMap.getOrDefault(notepad.getId(), 0)
        ));
    }

    private Map<Long, Integer> loadReadCountMap(List<Long> notepadIds) {
        if (notepadIds == null || notepadIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        Map<Long, Integer> readCountMap = new java.util.HashMap<>();
        List<NotepadRepository.NotepadReadCount> readCounts = notepadRepository.countReadConfirmsByNotepadIds(notepadIds);
        for (NotepadRepository.NotepadReadCount readCount : readCounts) {
            readCountMap.put(readCount.getNotepadId(), Math.toIntExact(readCount.getReadCount()));
        }
        return readCountMap;
    }
}
