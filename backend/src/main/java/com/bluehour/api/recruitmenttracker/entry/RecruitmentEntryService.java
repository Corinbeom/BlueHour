package com.bluehour.api.recruitmenttracker.entry;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.recruitmenttracker.entry.model.PlatformType;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentStep;
import com.bluehour.domain.recruitmenttracker.entry.port.RecruitmentEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import com.bluehour.domain.member.event.MemberDeletedEvent;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class RecruitmentEntryService {

    private final RecruitmentEntryRepository recruitmentEntryRepository;
    private final MemberRepository memberRepository;

    public RecruitmentEntryService(
            RecruitmentEntryRepository recruitmentEntryRepository,
            MemberRepository memberRepository
    ) {
        this.recruitmentEntryRepository = recruitmentEntryRepository;
        this.memberRepository = memberRepository;
    }

    public RecruitmentEntry create(
            Long memberId,
            String companyName,
            String position,
            RecruitmentStep step,
            PlatformType platformType,
            String externalId,
            LocalDate appliedDate
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        RecruitmentEntry entry = new RecruitmentEntry(member, companyName, position, step, platformType, externalId, appliedDate);
        return recruitmentEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public RecruitmentEntry get(Long id) {
        return recruitmentEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecruitmentEntry를 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public RecruitmentEntry get(Long id, Long memberId) {
        RecruitmentEntry entry = get(id);
        ensureOwner(entry, memberId);
        return entry;
    }

    @Transactional(readOnly = true)
    public List<RecruitmentEntry> listByMember(Long memberId) {
        return recruitmentEntryRepository.findAllByMemberId(memberId);
    }

    public RecruitmentEntry update(
            Long id,
            String companyName,
            String position,
            RecruitmentStep step,
            PlatformType platformType,
            String externalId,
            LocalDate appliedDate
    ) {
        RecruitmentEntry entry = get(id);
        return updateEntry(entry, companyName, position, step, platformType, externalId, appliedDate);
    }

    public RecruitmentEntry update(
            Long id,
            Long memberId,
            String companyName,
            String position,
            RecruitmentStep step,
            PlatformType platformType,
            String externalId,
            LocalDate appliedDate
    ) {
        RecruitmentEntry entry = get(id, memberId);
        return updateEntry(entry, companyName, position, step, platformType, externalId, appliedDate);
    }

    private RecruitmentEntry updateEntry(
            RecruitmentEntry entry,
            String companyName,
            String position,
            RecruitmentStep step,
            PlatformType platformType,
            String externalId,
            LocalDate appliedDate
    ) {
        entry.updateApplicationInfo(companyName, position);
        if (step != null) entry.changeStep(step);
        if (externalId != null || platformType != null) {
            entry.linkExternal(externalId, platformType);
        }
        if (appliedDate != null) entry.changeAppliedDate(appliedDate);
        return entry;
    }

    public RecruitmentEntry changeStep(Long id, RecruitmentStep step) {
        RecruitmentEntry entry = get(id);
        entry.changeStep(step);
        return entry;
    }

    public RecruitmentEntry changeStep(Long id, Long memberId, RecruitmentStep step) {
        RecruitmentEntry entry = get(id, memberId);
        entry.changeStep(step);
        return entry;
    }

    public void delete(Long id) {
        RecruitmentEntry entry = get(id);
        recruitmentEntryRepository.delete(entry);
    }

    public void delete(Long id, Long memberId) {
        RecruitmentEntry entry = get(id, memberId);
        recruitmentEntryRepository.delete(entry);
    }

    @EventListener
    public void onMemberDeleted(MemberDeletedEvent event) {
        listByMember(event.memberId()).forEach(entry -> delete(entry.getId()));
    }

    private void ensureOwner(RecruitmentEntry entry, Long memberId) {
        if (!entry.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("지원 항목에 접근할 권한이 없습니다.");
        }
    }
}
