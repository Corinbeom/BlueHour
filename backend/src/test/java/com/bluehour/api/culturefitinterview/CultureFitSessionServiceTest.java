package com.bluehour.api.culturefitinterview;

import com.bluehour.api.culturefitinterview.dto.CultureFitSessionCreateRequest;
import com.bluehour.domain.culturefitinterview.model.CultureFitFeedbackStatus;
import com.bluehour.domain.culturefitinterview.model.CultureFitQuestion;
import com.bluehour.domain.culturefitinterview.model.CultureFitSession;
import com.bluehour.domain.culturefitinterview.port.CultureFitSessionRepository;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.session.port.InterviewAiPort;
import com.bluehour.domain.resume.session.port.UrlTextFetcherPort;
import com.bluehour.domain.resume.session.service.PositionPromptRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CultureFitSessionServiceTest {

    @Mock CultureFitSessionRepository sessionRepo;
    @Mock MemberRepository memberRepo;
    @Mock InterviewAiPort aiPort;
    @Mock PositionPromptRegistry promptRegistry;
    @Mock UrlTextFetcherPort urlTextFetcher;

    CultureFitSessionService sut;
    Member member;

    @BeforeEach
    void setUp() {
        sut = new CultureFitSessionService(sessionRepo, memberRepo, aiPort, promptRegistry, urlTextFetcher);
        member = new Member("test@example.com");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Test
    @DisplayName("createSession: 컬처핏 시스템 프롬프트로 질문을 생성하고 저장한다")
    void createSession_generatesQuestions() {
        given(memberRepo.findById(1L)).willReturn(Optional.of(member));
        given(promptRegistry.systemInstructionFor("CULTURE_FIT")).willReturn("culture system");
        given(aiPort.generateCultureFitQuestions(eq("culture system"), any(), any()))
                .willReturn(List.of(new InterviewAiPort.GeneratedQuestion(
                        "주도성", 90, "빠르게 실행한 경험은?", "실행 방식 검증", "실행, 주도성", "예시 답변"
                )));
        given(sessionRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        CultureFitSession session = sut.createSession(1L, new CultureFitSessionCreateRequest(
                "테스트 회사",
                "이 회사는 빠른 실행과 투명한 공유를 중요하게 여기며 고객 문제를 직접 확인하는 문화를 갖고 있습니다. 팀 간 협업과 주도적인 문제 해결을 기대합니다.",
                "PM JD",
                "PM"
        ));

        assertThat(session.getQuestions()).hasSize(1);
        assertThat(session.getQuestions().get(0).getQuestionText()).isEqualTo("빠르게 실행한 경험은?");
        verify(aiPort).generateCultureFitQuestions("culture system", session.getCompanyCultureText(), session.getJobDescriptionText());
    }

    @Test
    @DisplayName("generateFeedback: 답변을 기업 문화와 함께 평가하고 alignmentNote를 저장한다")
    void generateFeedback_storesAlignmentNote() {
        CultureFitSession session = new CultureFitSession(
                member,
                "테스트 회사",
                "고객 중심과 빠른 실행을 중요하게 여기는 회사입니다.",
                "JD",
                "PM"
        );
        CultureFitQuestion question = new CultureFitQuestion(
                0, "고객 중심", 80, "고객 중심 경험은?", "문화 정렬 검증", "고객", null
        );
        session.addQuestion(question);
        ReflectionTestUtils.setField(question, "id", 10L);
        question.submitAnswer("고객 인터뷰 결과를 바탕으로 우선순위를 바꿨습니다.");

        given(sessionRepo.findById(100L)).willReturn(Optional.of(session));
        given(promptRegistry.systemInstructionFor("CULTURE_FIT")).willReturn("culture system");
        given(aiPort.generateCultureFitFeedback(any(), any(), any(), any(), any()))
                .willReturn(new InterviewAiPort.GeneratedCultureFitFeedback(
                        List.of("고객 근거가 분명합니다."),
                        List.of("성과 지표를 추가하세요."),
                        "개선 답변",
                        List.of("어떤 지표가 바뀌었나요?"),
                        "고객 중심 가치와 잘 정렬됩니다."
                ));
        given(sessionRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        CultureFitQuestion updated = sut.generateFeedback(1L, 100L, 10L);

        assertThat(updated.getFeedbackStatus()).isEqualTo(CultureFitFeedbackStatus.COMPLETED);
        assertThat(updated.getFeedback().getAlignmentNote()).contains("고객 중심 가치");
    }
}
