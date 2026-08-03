package com.bluehour.api.culturefitinterview;

import com.bluehour.api.culturefitinterview.dto.CultureFitAnswerRequest;
import com.bluehour.api.culturefitinterview.dto.CultureFitQuestionResponse;
import com.bluehour.api.culturefitinterview.dto.CultureFitScrapeRequest;
import com.bluehour.api.culturefitinterview.dto.CultureFitScrapeResponse;
import com.bluehour.api.culturefitinterview.dto.CultureFitSessionCreateRequest;
import com.bluehour.api.culturefitinterview.dto.CultureFitSessionResponse;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.culturefitinterview.model.CultureFitSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "컬처핏 면접", description = "기업 문화 기반 컬처핏 면접 질문 생성 및 피드백")
@RestController
@RequestMapping("/api/culture-fit")
public class CultureFitSessionController {

    private final CultureFitSessionService service;

    public CultureFitSessionController(CultureFitSessionService service) {
        this.service = service;
    }

    @Operation(summary = "기업 문화 URL 텍스트 추출")
    @PostMapping("/scrape")
    public ApiResponse<CultureFitScrapeResponse> scrape(@Valid @RequestBody CultureFitScrapeRequest request) {
        return ApiResponse.success(new CultureFitScrapeResponse(service.scrape(request.url())));
    }

    @Operation(summary = "컬처핏 세션 생성")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CultureFitSessionResponse> create(@Valid @RequestBody CultureFitSessionCreateRequest request) {
        Long memberId = AuthUtils.currentMemberId();
        CultureFitSession session = service.createSession(memberId, request);
        return ApiResponse.success(CultureFitSessionResponse.from(session));
    }

    @Operation(summary = "내 컬처핏 세션 목록 조회")
    @GetMapping("/sessions")
    public ApiResponse<List<CultureFitSessionResponse>> list() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.listSessions(memberId).stream()
                .map(CultureFitSessionResponse::from)
                .toList());
    }

    @Operation(summary = "컬처핏 세션 상세 조회")
    @GetMapping("/sessions/{id}")
    public ApiResponse<CultureFitSessionResponse> get(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(CultureFitSessionResponse.from(service.getSession(memberId, id)));
    }

    @Operation(summary = "컬처핏 질문 목록 조회")
    @GetMapping("/sessions/{id}/questions")
    public ApiResponse<List<CultureFitQuestionResponse>> questions(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(service.getQuestions(memberId, id).stream()
                .map(CultureFitQuestionResponse::from)
                .toList());
    }

    @Operation(summary = "컬처핏 답변 제출")
    @PostMapping("/sessions/{id}/questions/{questionId}/answer")
    public ApiResponse<CultureFitQuestionResponse> answer(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @Valid @RequestBody CultureFitAnswerRequest request
    ) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(CultureFitQuestionResponse.from(
                service.submitAnswer(memberId, id, questionId, request.answerText())
        ));
    }

    @Operation(summary = "컬처핏 피드백 생성")
    @PostMapping("/sessions/{id}/questions/{questionId}/feedback")
    public ApiResponse<CultureFitQuestionResponse> feedback(
            @PathVariable Long id,
            @PathVariable Long questionId
    ) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(CultureFitQuestionResponse.from(
                service.generateFeedback(memberId, id, questionId)
        ));
    }

    @Operation(summary = "컬처핏 세션 완료")
    @PutMapping("/sessions/{id}/complete")
    public ApiResponse<CultureFitSessionResponse> complete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(CultureFitSessionResponse.from(service.completeSession(memberId, id)));
    }
}
