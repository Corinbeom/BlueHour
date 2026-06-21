package com.bluehour.api.studyquiz.session;

import com.bluehour.api.studyquiz.session.dto.CreateCsQuizSessionRequest;
import com.bluehour.api.studyquiz.session.dto.CsQuizSessionResponse;
import com.bluehour.api.studyquiz.session.dto.CsQuizStatsResponse;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.studyquiz.session.model.CsQuizSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "CS 퀴즈 세션", description = "CS 퀴즈 세션 생성, 조회, 통계")
@RestController
@RequestMapping("/api/cs-quiz-sessions")
public class CsQuizSessionController {

    private final CsQuizSessionService service;

    public CsQuizSessionController(CsQuizSessionService service) {
        this.service = service;
    }

    @Operation(summary = "내 퀴즈 세션 목록", description = "로그인한 사용자의 CS 퀴즈 세션 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<CsQuizSessionResponse>> listByCurrentMember() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.listByMemberCached(memberId));
    }

    @Operation(summary = "퀴즈 세션 생성", description = "난이도, 토픽, 문제 수를 지정하여 AI가 CS 퀴즈를 생성합니다.")
    @PostMapping
    public ApiResponse<CsQuizSessionResponse> create(@Valid @RequestBody CreateCsQuizSessionRequest req) {
        Long memberId = AuthUtils.currentMemberId();
        CsQuizSession created = service.create(
                memberId,
                req.difficulty(),
                req.topics(),
                req.questionCount(),
                req.title()
        );
        return ApiResponse.success(CsQuizSessionResponse.from(created));
    }

    @Operation(summary = "퀴즈 통계 조회", description = "사용자의 전체 퀴즈 정답률과 토픽별 통계를 조회합니다.")
    @GetMapping("/stats")
    public ApiResponse<CsQuizStatsResponse> stats() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getStats(memberId));
    }

    @Operation(summary = "퀴즈 세션 상세 조회", description = "세션 ID로 퀴즈 문제 포함 세션 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<CsQuizSessionResponse> get(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(CsQuizSessionResponse.from(service.get(id, memberId)));
    }

    @Operation(summary = "퀴즈 세션 삭제", description = "퀴즈 세션을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        service.delete(id, memberId);
        return ApiResponse.success(null);
    }
}
