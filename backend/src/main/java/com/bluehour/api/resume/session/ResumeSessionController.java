package com.bluehour.api.resume.session;

import com.bluehour.api.resume.session.dto.CreateResumeSessionRequest;
import com.bluehour.api.resume.session.dto.JdMatchAnalysisResponse;
import com.bluehour.api.resume.session.dto.JdMatchRequest;
import com.bluehour.api.resume.session.dto.ResumeInterviewStatsResponse;
import com.bluehour.api.resume.session.dto.ResumeSessionResponse;
import com.bluehour.api.resume.session.dto.CoachingReportResponse;
import com.bluehour.api.resume.session.dto.SessionReportResponse;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.resume.session.model.ResumeSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "이력서 분석 세션", description = "이력서 기반 AI 면접 질문 생성 세션")
@RestController
@RequestMapping("/api/resume-sessions")
public class ResumeSessionController {

    private final ResumeSessionService service;

    public ResumeSessionController(ResumeSessionService service) {
        this.service = service;
    }

    @Operation(summary = "면접 연습 통계 조회", description = "배지별 강점/개선점 통계를 조회합니다.")
    @GetMapping("/stats")
    public ApiResponse<ResumeInterviewStatsResponse> interviewStats() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getInterviewStats(memberId));
    }

    @Operation(summary = "내 세션 목록 조회", description = "로그인한 사용자의 이력서 분석 세션 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<ResumeSessionResponse>> listByCurrentMember() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.listByMemberCached(memberId));
    }

    @Operation(summary = "분석 세션 생성", description = "업로드된 이력서를 기반으로 AI 면접 질문을 생성합니다.")
    @PostMapping
    public ApiResponse<ResumeSessionResponse> create(@Valid @RequestBody CreateResumeSessionRequest request) {
        Long memberId = AuthUtils.currentMemberId();
        ResumeSession created = service.createFromResume(
                memberId,
                request.positionType(),
                request.title(),
                request.resumeId(),
                request.portfolioResumeId(),
                request.portfolioUrl(),
                request.targetTechnologies()
        );
        return ApiResponse.success(ResumeSessionResponse.from(created));
    }

    @Operation(summary = "세션 상세 조회", description = "세션 ID로 면접 질문 포함 세션 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<ResumeSessionResponse> get(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getResponse(id, memberId));
    }

    @Operation(summary = "세션 완료 처리", description = "현재 세션을 종료 상태(COMPLETED)로 전환합니다. 미답변 질문이 있어도 종료할 수 있습니다.")
    @PostMapping("/{id}/complete")
    public ApiResponse<ResumeSessionResponse> complete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.complete(id, memberId));
    }

    @Operation(summary = "AI 회고 리포트 생성", description = "세션 데이터를 분석하여 AI 회고 리포트를 생성합니다. 이미 생성된 리포트가 있으면 캐시된 결과를 반환합니다.")
    @PostMapping("/{id}/report")
    public ApiResponse<SessionReportResponse> generateReport(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.generateReport(id, memberId));
    }

    @Operation(summary = "AI 회고 리포트 조회", description = "이미 생성된 AI 회고 리포트를 조회합니다.")
    @GetMapping("/{id}/report")
    public ApiResponse<SessionReportResponse> getReport(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getReport(id, memberId));
    }

    @Operation(summary = "AI 누적 코칭 리포트 조회", description = "이미 생성된 AI 코칭 리포트를 조회합니다. 없으면 null을 반환합니다.")
    @GetMapping("/coaching-report")
    public ApiResponse<CoachingReportResponse> getCoachingReport() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getCoachingReport(memberId));
    }

    @Operation(summary = "AI 누적 코칭 리포트 생성/재생성", description = "완료된 모든 세션 리포트를 종합 분석하여 장기 성장 추이 + 맞춤 학습 계획을 생성합니다. 기존 리포트를 덮어씁니다.")
    @PostMapping("/coaching-report")
    public ApiResponse<CoachingReportResponse> generateCoachingReport() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.generateCoachingReport(memberId));
    }

    @Operation(summary = "공고-이력서 매칭 분석", description = "이력서와 채용공고(JD) 텍스트를 AI로 분석하여 매칭률·매칭 키워드·부족 키워드를 반환합니다. 결과는 저장되지 않습니다.")
    @PostMapping("/jd-match")
    public ApiResponse<JdMatchAnalysisResponse> analyzeJdMatch(@Valid @RequestBody JdMatchRequest request) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.analyzeJdMatch(memberId, request));
    }

    @Operation(summary = "세션 삭제", description = "이력서 분석 세션을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        service.delete(id, memberId);
        return ApiResponse.success(null);
    }
}
