package com.bluehour.infra.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bluehour.domain.coach.port.CoachAiPort;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptBuilderTest {

    @Test
    @DisplayName("buildQuestionsPrompt: targetTechnologies가 null이면 [TargetTechnologies] 섹션 미포함")
    void buildQuestionsPrompt_기본() {
        String prompt = AiPromptBuilder.buildQuestionsPrompt(
                "BE", "이력서 내용", null, null, null);

        assertThat(prompt).contains("[ResumeText]");
        assertThat(prompt).contains("이력서 내용");
        assertThat(prompt).doesNotContain("[TargetTechnologies]");
    }

    @Test
    @DisplayName("buildQuestionsPrompt: 빈 리스트일 때도 [TargetTechnologies] 섹션 미포함")
    void buildQuestionsPrompt_빈리스트() {
        String prompt = AiPromptBuilder.buildQuestionsPrompt(
                "BE", "이력서 내용", null, null, Collections.emptyList());

        assertThat(prompt).doesNotContain("[TargetTechnologies]");
    }

    @Test
    @DisplayName("buildQuestionsPrompt: 기술 3개 전달 시 [TargetTechnologies] 섹션 + CSV 포맷 + 규칙 텍스트 포함")
    void buildQuestionsPrompt_기술스택포함() {
        List<String> techs = List.of("Java", "Spring", "Kubernetes");

        String prompt = AiPromptBuilder.buildQuestionsPrompt(
                "BE", "이력서 내용", null, null, techs);

        assertThat(prompt).contains("[TargetTechnologies]");
        assertThat(prompt).contains("Java, Spring, Kubernetes");
        assertThat(prompt).contains("채용공고에 명시된 요구 기술");
        assertThat(prompt).contains("채용공고 기술");
    }

    @Test
    @DisplayName("buildQuestionsPrompt: resumeText가 [ResumeText] 섹션에 포함")
    void buildQuestionsPrompt_이력서텍스트포함() {
        String resumeText = "3년차 백엔드 개발자, Spring Boot 경험";

        String prompt = AiPromptBuilder.buildQuestionsPrompt(
                "BE", resumeText, null, null, null);

        assertThat(prompt).contains("[ResumeText]");
        assertThat(prompt).contains(resumeText);
    }

    @Test
    @DisplayName("buildQuestionsPrompt: portfolioText/portfolioUrl 각각 포함")
    void buildQuestionsPrompt_포트폴리오텍스트포함() {
        String portfolioText = "DevWeb 프로젝트 — AI 면접 질문 생성 플랫폼";
        String portfolioUrl = "https://github.com/example/devweb";

        String prompt = AiPromptBuilder.buildQuestionsPrompt(
                "BE", "이력서", portfolioText, portfolioUrl, null);

        assertThat(prompt).contains("[PortfolioText]");
        assertThat(prompt).contains(portfolioText);
        assertThat(prompt).contains("[PortfolioUrl]");
        assertThat(prompt).contains(portfolioUrl);
    }

    @Test
    @DisplayName("buildFeedbackPrompt: 모든 파라미터가 프롬프트에 포함")
    void buildFeedbackPrompt_정상() {
        String prompt = AiPromptBuilder.buildFeedbackPrompt(
                "질문 본문", "출제 의도", "키워드1, 키워드2",
                "모범 답안 텍스트", "사용자 답변 텍스트");

        assertThat(prompt).contains("[Question]");
        assertThat(prompt).contains("질문 본문");
        assertThat(prompt).contains("[Intention]");
        assertThat(prompt).contains("출제 의도");
        assertThat(prompt).contains("[Keywords]");
        assertThat(prompt).contains("키워드1, 키워드2");
        assertThat(prompt).contains("[ModelAnswer]");
        assertThat(prompt).contains("모범 답안 텍스트");
        assertThat(prompt).contains("[UserAnswer]");
        assertThat(prompt).contains("사용자 답변 텍스트");
    }

    @Test
    @DisplayName("buildCultureFitQuestionPrompt: 기업 문화와 JD 기반 질문 생성을 지시한다")
    void buildCultureFitQuestionPrompt_정상() {
        String prompt = AiPromptBuilder.buildCultureFitQuestionPrompt(
                "빠른 실행과 투명한 공유를 중요하게 여기는 회사입니다.",
                "프로덕트 매니저 JD"
        );

        assertThat(prompt).contains("[기업 문화/가치]");
        assertThat(prompt).contains("빠른 실행과 투명한 공유");
        assertThat(prompt).contains("[채용공고(JD)]");
        assertThat(prompt).contains("프로덕트 매니저 JD");
        assertThat(prompt).contains("STAR 방식");
    }

    @Test
    @DisplayName("buildCultureFitFeedbackPrompt: 문화 정렬도 평가 필드를 요구한다")
    void buildCultureFitFeedbackPrompt_정상() {
        String prompt = AiPromptBuilder.buildCultureFitFeedbackPrompt(
                "고객 중심과 주도성을 중요하게 여깁니다.",
                null,
                "고객 중심 경험을 설명해 주세요.",
                "사용자 인터뷰를 바탕으로 우선순위를 바꾼 경험이 있습니다."
        );

        assertThat(prompt).contains("alignmentNote");
        assertThat(prompt).contains("고객 중심과 주도성");
        assertThat(prompt).contains("제공되지 않음");
        assertThat(prompt).contains("기업의 특정 가치나 일하는 방식");
    }

    @Test
    @DisplayName("buildCoachAnalysisPrompt: 개발 직무는 CS 퀴즈를 핵심 준비 축으로 포함")
    void buildCoachAnalysisPrompt_개발직무() {
        String prompt = AiPromptBuilder.buildCoachAnalysisPrompt(new CoachAiPort.CoachContext(
                List.of("백엔드 개발자"),
                "DEVELOPER",
                true,
                1,
                Map.of("APPLIED", 1),
                1,
                2,
                0,
                0,
                Map.of("OS", 0.5),
                10
        ));

        assertThat(prompt).contains("기술트랙: true");
        assertThat(prompt).contains("CS 퀴즈 정확도와 기술 지식 학습을 핵심 준비 축");
        assertThat(prompt).contains("OS 퀴즈 10문제");
    }

    @Test
    @DisplayName("buildCoachAnalysisPrompt: 비개발 직무는 CS 퀴즈를 핵심 평가 기준으로 쓰지 않음")
    void buildCoachAnalysisPrompt_비개발직무() {
        String prompt = AiPromptBuilder.buildCoachAnalysisPrompt(new CoachAiPort.CoachContext(
                List.of("UX/UI 디자이너"),
                "DESIGN",
                false,
                1,
                Map.of("APPLIED", 1),
                1,
                2,
                0,
                0,
                Map.of("OS", 0.5),
                10
        ));

        assertThat(prompt).contains("기술트랙: false");
        assertThat(prompt).contains("CS 퀴즈를 핵심 평가 기준으로 쓰지 마세요");
        assertThat(prompt).contains("포트폴리오/경험 정리");
    }

    @Test
    @DisplayName("nullToEmpty: null이면 빈 문자열 반환")
    void nullToEmpty_null이면_빈문자열() {
        assertThat(AiPromptBuilder.nullToEmpty(null)).isEqualTo("");
    }

    @Test
    @DisplayName("nullToEmpty: 값이 있으면 그대로 반환")
    void nullToEmpty_값이있으면_그대로() {
        assertThat(AiPromptBuilder.nullToEmpty("hello")).isEqualTo("hello");
    }
}
