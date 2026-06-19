package com.bluehour.api.recruitmenttracker.entry;

import com.bluehour.api.recruitmenttracker.entry.dto.*;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "지원 현황", description = "채용 지원 현황 CRUD")
@RestController
@RequestMapping("/api/recruitment-entries")
public class RecruitmentEntryController {

    private final RecruitmentEntryService service;

    public RecruitmentEntryController(RecruitmentEntryService service) {
        this.service = service;
    }

    @Operation(summary = "지원 항목 생성", description = "새로운 채용 지원 항목을 생성합니다.")
    @PostMapping
    public ApiResponse<RecruitmentEntryResponse> create(@Valid @RequestBody CreateRecruitmentEntryRequest req) {
        Long memberId = AuthUtils.currentMemberId();
        RecruitmentEntry created = service.create(
                memberId,
                req.companyName(),
                req.position(),
                req.step(),
                req.platformType(),
                req.externalId(),
                req.appliedDate()
        );
        return ApiResponse.success(RecruitmentEntryResponse.from(created));
    }

    @Operation(summary = "지원 항목 조회", description = "ID로 지원 항목을 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<RecruitmentEntryResponse> get(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(RecruitmentEntryResponse.from(service.get(id, memberId)));
    }

    @Operation(summary = "내 지원 목록 조회", description = "로그인한 사용자의 모든 지원 항목을 조회합니다.")
    @GetMapping("/by-member/me")
    public ApiResponse<List<RecruitmentEntryResponse>> listByCurrentMember() {
        Long memberId = AuthUtils.currentMemberId();
        List<RecruitmentEntryResponse> result = service.listByMember(memberId).stream()
                .map(RecruitmentEntryResponse::from)
                .toList();
        return ApiResponse.success(result);
    }

    @Operation(summary = "지원 항목 수정", description = "지원 항목의 전체 정보를 수정합니다.")
    @PutMapping("/{id}")
    public ApiResponse<RecruitmentEntryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecruitmentEntryRequest req
    ) {
        Long memberId = AuthUtils.currentMemberId();
        RecruitmentEntry updated = service.update(
                id,
                memberId,
                req.companyName(),
                req.position(),
                req.step(),
                req.platformType(),
                req.externalId(),
                req.appliedDate()
        );
        return ApiResponse.success(RecruitmentEntryResponse.from(updated));
    }

    @Operation(summary = "채용 단계 변경", description = "지원 항목의 채용 단계(step)만 변경합니다.")
    @PatchMapping("/{id}/step")
    public ApiResponse<RecruitmentEntryResponse> changeStep(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecruitmentStepRequest req
    ) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(RecruitmentEntryResponse.from(service.changeStep(id, memberId, req.step())));
    }

    @Operation(summary = "지원 항목 삭제", description = "지원 항목을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        service.delete(id, memberId);
        return ApiResponse.ok();
    }
}
