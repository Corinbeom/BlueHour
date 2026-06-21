package com.bluehour.api.coach;

import com.bluehour.api.coach.dto.CoachAnalysisResponse;
import com.bluehour.api.coach.dto.CoachSummaryResponse;
import com.bluehour.domain.coach.port.CoachAiPort;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.recruitmenttracker.entry.model.PlatformType;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentStep;
import com.bluehour.domain.recruitmenttracker.entry.port.RecruitmentEntryRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeFileType;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.speechinterview.model.SpeechInterviewQuestion;
import com.bluehour.domain.speechinterview.model.SpeechInterviewSession;
import com.bluehour.domain.speechinterview.port.SpeechInterviewSessionRepository;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;
import com.bluehour.domain.studyquiz.session.port.CsQuizSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CoachServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock RecruitmentEntryRepository recruitmentEntryRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock SpeechInterviewSessionRepository speechInterviewSessionRepository;
    @Mock CsQuizSessionRepository csQuizSessionRepository;
    @Mock CoachAiPort coachAiPort;

    CoachService sut;

    Member member;

    @BeforeEach
    void setUp() throws Exception {
        sut = new CoachService(
                memberRepository,
                recruitmentEntryRepository,
                resumeRepository,
                speechInterviewSessionRepository,
                csQuizSessionRepository,
                coachAiPort,
                new ConcurrentMapCacheManager("coachSummary", "coachAnalysis")
        );
        member = new Member("user@example.com");
        setId(member, 1L);
    }

    @Test
    @DisplayName("summary는 4개 도메인 데이터를 집계한다")
    void getSummary_집계_성공() throws Exception {
        member.completeOnboarding(List.of("백엔드 개발자"));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(recruitmentEntryRepository.findAllByMemberId(1L)).willReturn(List.of(
                entry(10L, "백엔드 개발자", RecruitmentStep.APPLIED, LocalDate.of(2026, 6, 10)),
                entry(11L, "플랫폼 엔지니어", RecruitmentStep.INTERVIEWING, LocalDate.of(2026, 6, 11))
        ));
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of(resume(LocalDateTime.of(2026, 6, 9, 10, 0))));
        given(speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of(completedInterview(2)));
        given(csQuizSessionRepository.findStatsGroupedByTopic(1L)).willReturn(List.of(
                new Object[]{CsQuizTopic.OS, 10L, 4L},
                new Object[]{CsQuizTopic.NETWORK, 5L, 4L}
        ));

        CoachSummaryResponse result = sut.getSummary(1L);

        assertThat(result.targetRoles()).containsExactly("백엔드 개발자");
        assertThat(result.inferredFrom()).isEqualTo("TARGET_ROLES");
        assertThat(result.roleCategory()).isEqualTo("DEVELOPER");
        assertThat(result.technicalTrack()).isTrue();
        assertThat(result.recruitment().totalApplications()).isEqualTo(2);
        assertThat(result.recruitment().statusBreakdown()).containsEntry("APPLIED", 1).containsEntry("INTERVIEWING", 1);
        assertThat(result.resume().uploadedCount()).isEqualTo(1);
        assertThat(result.interview().completedSessions()).isEqualTo(1);
        assertThat(result.interview().averageTurns()).isEqualTo(2.0);
        assertThat(result.quiz().totalAttempts()).isEqualTo(15);
        assertThat(result.quiz().topicAccuracy()).containsEntry("OS", 0.4).containsEntry("NETWORK", 0.8);
        assertThat(result.quiz().topicAttempts()).containsEntry("OS", 10).containsEntry("NETWORK", 5);
    }

    @Test
    @DisplayName("targetRoles와 JD 추론값이 모두 없으면 AI를 호출하지 않는다")
    void getAnalysis_직무_없으면_AI_미호출() throws Exception {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(recruitmentEntryRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of());
        given(csQuizSessionRepository.findStatsGroupedByTopic(1L)).willReturn(List.of());

        CoachAnalysisResponse result = sut.getAnalysis(1L);

        assertThat(result.needsTargetRoles()).isTrue();
        assertThat(result.today()).contains("관심 직무");
        then(coachAiPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("analysis는 contextHash 기준으로 캐시되어 동일 지표에서 AI를 재호출하지 않는다")
    void getAnalysis_캐시_성공() throws Exception {
        member.completeOnboarding(List.of("백엔드 개발자"));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(recruitmentEntryRepository.findAllByMemberId(1L)).willReturn(List.of(entry(10L, "백엔드 개발자", RecruitmentStep.APPLIED, LocalDate.of(2026, 6, 10))));
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of());
        given(csQuizSessionRepository.findStatsGroupedByTopic(1L)).willReturn(List.of());
        given(coachAiPort.analyzeReadiness(any())).willReturn(new CoachAiPort.GeneratedCoachAnalysis(
                68,
                "백엔드 개발자",
                List.of("지원 시작"),
                List.of("면접 부족"),
                List.of(
                        new CoachAiPort.PlanItem(1, "면접 연습 1회"),
                        new CoachAiPort.PlanItem(2, "이력서 보강"),
                        new CoachAiPort.PlanItem(3, "퀴즈 10문제")
                ),
                "면접 연습 1회"
        ));

        CoachAnalysisResponse first = sut.getAnalysis(1L);
        CoachAnalysisResponse second = sut.getAnalysis(1L);

        assertThat(first.score()).isEqualTo(68);
        assertThat(second.today()).isEqualTo("면접 연습 1회");
        then(coachAiPort).should().analyzeReadiness(any());
    }

    @Test
    @DisplayName("개발 직무는 AI 컨텍스트에 기술 트랙으로 전달한다")
    void getAnalysis_개발직무_기술트랙() throws Exception {
        member.completeOnboarding(List.of("프론트엔드 개발자"));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(recruitmentEntryRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of());
        given(csQuizSessionRepository.findStatsGroupedByTopic(1L)).willReturn(List.<Object[]>of(
                new Object[]{CsQuizTopic.OS, 10L, 6L}
        ));
        given(coachAiPort.analyzeReadiness(any())).willReturn(analysis());

        sut.getAnalysis(1L);

        var captor = forClass(CoachAiPort.CoachContext.class);
        then(coachAiPort).should().analyzeReadiness(captor.capture());
        assertThat(captor.getValue().roleCategory()).isEqualTo("DEVELOPER");
        assertThat(captor.getValue().technicalTrack()).isTrue();
        assertThat(captor.getValue().quizTotalAttempts()).isEqualTo(10);
    }

    @Test
    @DisplayName("비개발 직무는 AI 컨텍스트에 비기술 트랙으로 전달한다")
    void getAnalysis_비개발직무_비기술트랙() throws Exception {
        member.completeOnboarding(List.of("UX/UI 디자이너"));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(recruitmentEntryRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of());
        given(speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(1L)).willReturn(List.of());
        given(csQuizSessionRepository.findStatsGroupedByTopic(1L)).willReturn(List.<Object[]>of(
                new Object[]{CsQuizTopic.NETWORK, 4L, 2L}
        ));
        given(coachAiPort.analyzeReadiness(any())).willReturn(analysis());

        sut.getAnalysis(1L);

        var captor = forClass(CoachAiPort.CoachContext.class);
        then(coachAiPort).should().analyzeReadiness(captor.capture());
        assertThat(captor.getValue().roleCategory()).isEqualTo("DESIGN");
        assertThat(captor.getValue().technicalTrack()).isFalse();
        assertThat(captor.getValue().quizTotalAttempts()).isEqualTo(4);
    }

    private RecruitmentEntry entry(Long id, String position, RecruitmentStep step, LocalDate appliedDate) throws Exception {
        RecruitmentEntry entry = new RecruitmentEntry(member, "회사", position, step, PlatformType.MANUAL, null, appliedDate);
        setId(entry, id);
        return entry;
    }

    private Resume resume(LocalDateTime createdAt) throws Exception {
        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        resume.markExtracted("이력서 본문");
        setField(resume, "createdAt", createdAt);
        return resume;
    }

    private SpeechInterviewSession completedInterview(int questions) {
        SpeechInterviewSession session = new SpeechInterviewSession(member, "면접", "백엔드 개발자", null);
        for (int i = 0; i < questions; i++) {
            session.addQuestion(new SpeechInterviewQuestion(i, "기술", "질문" + i, "의도", "키워드", "답변"));
        }
        session.startInterview();
        session.complete();
        return session;
    }

    private CoachAiPort.GeneratedCoachAnalysis analysis() {
        return new CoachAiPort.GeneratedCoachAnalysis(
                70,
                "분석 직무",
                List.of("지원 시작"),
                List.of("면접 부족"),
                List.of(
                        new CoachAiPort.PlanItem(1, "이력서 보강"),
                        new CoachAiPort.PlanItem(2, "지원 현황 정리"),
                        new CoachAiPort.PlanItem(3, "면접 연습 1회")
                ),
                "이력서 보강"
        );
    }

    private void setId(Object entity, Long id) throws Exception {
        setField(entity, "id", id);
    }

    private void setField(Object entity, String name, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(entity, value);
    }
}
