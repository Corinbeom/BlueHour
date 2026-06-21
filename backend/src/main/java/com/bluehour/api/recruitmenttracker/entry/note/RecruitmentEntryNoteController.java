package com.bluehour.api.recruitmenttracker.entry.note;

import com.bluehour.api.recruitmenttracker.entry.note.dto.CreateRecruitmentEntryNoteRequest;
import com.bluehour.api.recruitmenttracker.entry.note.dto.RecruitmentEntryNoteResponse;
import com.bluehour.api.recruitmenttracker.entry.note.dto.UpdateRecruitmentEntryNoteRequest;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.recruitmenttracker.note.model.RecruitmentEntryNote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "지원 메모", description = "채용 지원 항목별 메모 관리")
@RestController
@RequestMapping("/api/recruitment-entries/{entryId}/notes")
public class RecruitmentEntryNoteController {

    private final RecruitmentEntryNoteService service;

    public RecruitmentEntryNoteController(RecruitmentEntryNoteService service) {
        this.service = service;
    }

    @Operation(summary = "메모 목록 조회", description = "지원 항목에 달린 모든 메모를 조회합니다.")
    @GetMapping
    public ApiResponse<List<RecruitmentEntryNoteResponse>> list(@PathVariable Long entryId) {
        Long memberId = AuthUtils.currentMemberId();
        List<RecruitmentEntryNoteResponse> result = service.list(entryId, memberId).stream()
                .map(RecruitmentEntryNoteResponse::from)
                .toList();
        return ApiResponse.success(result);
    }

    @Operation(summary = "메모 생성", description = "지원 항목에 새 메모를 추가합니다.")
    @PostMapping
    public ApiResponse<RecruitmentEntryNoteResponse> create(
            @PathVariable Long entryId,
            @Valid @RequestBody CreateRecruitmentEntryNoteRequest req
    ) {
        Long memberId = AuthUtils.currentMemberId();
        RecruitmentEntryNote created = service.create(entryId, memberId, req.content());
        return ApiResponse.success(RecruitmentEntryNoteResponse.from(created));
    }

    @Operation(summary = "메모 수정", description = "메모 내용을 수정합니다.")
    @PutMapping("/{noteId}")
    public ApiResponse<RecruitmentEntryNoteResponse> update(
            @PathVariable Long entryId,
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateRecruitmentEntryNoteRequest req
    ) {
        Long memberId = AuthUtils.currentMemberId();
        RecruitmentEntryNote updated = service.update(entryId, memberId, noteId, req.content());
        return ApiResponse.success(RecruitmentEntryNoteResponse.from(updated));
    }

    @Operation(summary = "메모 삭제", description = "메모를 삭제합니다.")
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> delete(
            @PathVariable Long entryId,
            @PathVariable Long noteId
    ) {
        Long memberId = AuthUtils.currentMemberId();
        service.delete(entryId, memberId, noteId);
        return ApiResponse.ok();
    }
}

