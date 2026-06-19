package com.bluehour.api.resume.session;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeExtractStatus;
import com.bluehour.domain.resume.model.ResumeFileType;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.resume.session.model.*;
import com.bluehour.domain.resume.session.port.FileStoragePort;
import com.bluehour.domain.resume.session.port.ResumeSessionRepository;
import com.bluehour.domain.resume.session.port.TextExtractorPort;
import com.bluehour.domain.resume.session.port.UrlTextFetcherPort;
import com.bluehour.domain.resume.session.service.QuestionGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeSessionServiceTest {

    @Mock ResumeSessionRepository sessionRepository;
    @Mock MemberRepository memberRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock FileStoragePort fileStorage;
    @Mock TextExtractorPort textExtractor;
    @Mock UrlTextFetcherPort urlTextFetcher;
    @Mock QuestionGenerator questionGenerator;

    @InjectMocks ResumeSessionService sut;

    private Member member;
    private MockMultipartFile validResumeFile;

    @BeforeEach
    void setUp() {
        member = new Member("test@example.com");
        setId(member, 1L);
        validResumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                "application/pdf",
                "PDF 이력서 내용".getBytes()
        );
    }

    // ─────────────────────────────────────────────
    // 정상 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("이력서 파일만으로 세션 생성 성공")
    void create_성공_파일만() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        StoredFileRef resumeRef = new StoredFileRef("storage/resume/test.pdf", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(resumeRef);
        given(textExtractor.extract(any(), anyString())).willReturn("이력서 텍스트 내용");
        given(questionGenerator.generate(any(), anyString(), any(), any(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.create(1L, "BE", "내 이력서", validResumeFile, null, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ResumeSessionStatus.QUESTIONS_READY);
        assertThat(result.getPositionType()).isEqualTo("BE");
        then(urlTextFetcher).shouldHaveNoInteractions(); // URL 크롤링 미호출
    }

    @Test
    @DisplayName("이력서 파일 + 포트폴리오 URL로 세션 생성 성공")
    void create_성공_파일과_URL() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        StoredFileRef resumeRef = new StoredFileRef("storage/resume/test.pdf", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(resumeRef);
        given(textExtractor.extract(any(), anyString())).willReturn("이력서 텍스트");
        given(urlTextFetcher.fetch("https://github.com/user")).willReturn("포트폴리오 텍스트");
        given(questionGenerator.generate(any(), anyString(), anyString(), anyString(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.create(1L, "FE", "내 이력서", validResumeFile, null, "https://github.com/user");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPortfolioText()).contains("포트폴리오 텍스트");
        then(urlTextFetcher).should().fetch("https://github.com/user");
    }

    @Test
    @DisplayName("이력서 파일 + 포트폴리오 파일로 세션 생성 성공")
    void create_성공_이력서와_포트폴리오_파일() {
        // given
        MockMultipartFile portfolioFile = new MockMultipartFile(
                "portfolioFile", "portfolio.pdf", "application/pdf", "포트폴리오 내용".getBytes()
        );

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        StoredFileRef ref = new StoredFileRef("key", "file.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(ref);
        given(textExtractor.extract(any(), anyString()))
                .willReturn("이력서 텍스트")
                .willReturn("포트폴리오 텍스트");
        given(questionGenerator.generate(any(), anyString(), anyString(), any(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.create(1L, "MOBILE", "모바일 이력서", validResumeFile, portfolioFile, null);

        // then
        assertThat(result).isNotNull();
        then(fileStorage).should(times(2)).save(any(), anyString(), anyString()); // 이력서 + 포트폴리오 2번 저장
    }

    @Test
    @DisplayName("title이 null이면 파일명을 title로 사용")
    void create_성공_title_null이면_파일명사용() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        StoredFileRef ref = new StoredFileRef("key", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(ref);
        given(textExtractor.extract(any(), anyString())).willReturn("이력서 텍스트");
        given(questionGenerator.generate(any(), anyString(), any(), any(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.create(1L, "BE", null, validResumeFile, null, null);

        // then
        assertThat(result.getTitle()).isEqualTo("resume.pdf");
    }

    // ─────────────────────────────────────────────
    // 유효성 검증 실패
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("10MB 초과 이력서 파일이면 IllegalArgumentException")
    void create_실패_파일크기_10MB초과() {
        // given
        byte[] bigContent = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile(
                "resumeFile", "big.pdf", "application/pdf", bigContent
        );

        // when & then
        assertThatThrownBy(() -> sut.create(1L, "BE", "테스트", bigFile, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("10MB 초과 포트폴리오 파일이면 IllegalArgumentException")
    void create_실패_포트폴리오_파일크기_10MB초과() {
        // given
        byte[] bigContent = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile bigPortfolio = new MockMultipartFile(
                "portfolioFile", "big_portfolio.pdf", "application/pdf", bigContent
        );

        // when & then
        assertThatThrownBy(() -> sut.create(1L, "BE", "테스트", validResumeFile, bigPortfolio, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("존재하지 않는 멤버 ID면 ResourceNotFoundException")
    void create_실패_존재하지않는_멤버() {
        // given
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.create(99L, "BE", "테스트", validResumeFile, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("빈 positionType 값이면 IllegalArgumentException")
    void create_실패_빈_포지션타입() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> sut.create(1L, "  ", "테스트", validResumeFile, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resumeFile이 null이면 IllegalArgumentException")
    void create_실패_resumeFile_null() {
        assertThatThrownBy(() -> sut.create(1L, "BE", "테스트", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumeFile");
    }

    @Test
    @DisplayName("resumeFile이 비어있으면 IllegalArgumentException")
    void create_실패_resumeFile_비어있음() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "resumeFile", "empty.pdf", "application/pdf", new byte[0]
        );

        assertThatThrownBy(() -> sut.create(1L, "BE", "테스트", emptyFile, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumeFile");
    }

    // ─────────────────────────────────────────────
    // get 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("세션 조회 성공")
    void get_성공() {
        // given
        ResumeSession session = new ResumeSession(member, "BE", "테스트", null);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        // when
        ResumeSession result = sut.get(1L);

        // then
        assertThat(result).isEqualTo(session);
    }

    @Test
    @DisplayName("존재하지 않는 세션 조회 시 ResourceNotFoundException")
    void get_실패_존재하지않는_세션() {
        given(sessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─────────────────────────────────────────────
    // createFromResume 정상 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("createFromResume - 사전 업로드된 이력서만으로 세션 생성 성공")
    void createFromResume_성공_이력서만() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Resume resume = new Resume(member, "내 이력서", ResumeFileType.RESUME);
        StoredFileRef resumeRef = new StoredFileRef("storage/resume/test.pdf", "resume.pdf", "application/pdf", 100L);
        resume.attachFile(resumeRef);
        resume.markExtracted("이력서 텍스트 내용");
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        given(questionGenerator.generate(any(), anyString(), any(), any(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.createFromResume(1L, "BE", "세션 제목", 10L, null, null, List.of());

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ResumeSessionStatus.QUESTIONS_READY);
        assertThat(result.getTitle()).isEqualTo("세션 제목");
        assertThat(result.getResumeText()).isEqualTo("이력서 텍스트 내용");
        then(urlTextFetcher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("createFromResume - 이력서 + 포트폴리오 Resume + URL 조합 성공")
    void createFromResume_성공_이력서_포트폴리오_URL() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        resume.attachFile(new StoredFileRef("key1", "resume.pdf", "application/pdf", 100L));
        resume.markExtracted("이력서 텍스트");
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        Resume portfolio = new Resume(member, "포트폴리오", ResumeFileType.PORTFOLIO);
        portfolio.attachFile(new StoredFileRef("key2", "portfolio.pdf", "application/pdf", 200L));
        portfolio.markExtracted("포트폴리오 텍스트");
        given(resumeRepository.findById(20L)).willReturn(Optional.of(portfolio));

        given(urlTextFetcher.fetch("https://github.com/user")).willReturn("URL 텍스트");
        given(questionGenerator.generate(any(), anyString(), anyString(), anyString(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.createFromResume(1L, "FE", "제목", 10L, 20L, "https://github.com/user", List.of());

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPortfolioText()).contains("포트폴리오 텍스트");
        assertThat(result.getPortfolioText()).contains("URL 텍스트");
    }

    @Test
    @DisplayName("createFromResume - title null이면 Resume.title 사용")
    void createFromResume_성공_title_null이면_Resume_title사용() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Resume resume = new Resume(member, "원래 이력서 제목", ResumeFileType.RESUME);
        resume.attachFile(new StoredFileRef("key", "resume.pdf", "application/pdf", 100L));
        resume.markExtracted("이력서 텍스트");
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        given(questionGenerator.generate(any(), anyString(), any(), any(), anyList()))
                .willReturn(List.of(dummyQuestion()));
        given(sessionRepository.save(any(ResumeSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ResumeSession result = sut.createFromResume(1L, "BE", null, 10L, null, null, List.of());

        // then
        assertThat(result.getTitle()).isEqualTo("원래 이력서 제목");
    }

    // ─────────────────────────────────────────────
    // createFromResume 실패 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("createFromResume - 존재하지 않는 resumeId → ResourceNotFoundException")
    void createFromResume_실패_존재하지않는_resumeId() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(resumeRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.createFromResume(1L, "BE", "제목", 99L, null, null, List.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("createFromResume - 추출 미완료 이력서 → IllegalArgumentException")
    void createFromResume_실패_추출미완료() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        // extractStatus = PENDING (markExtracted 호출하지 않음)
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        // when & then
        assertThatThrownBy(() -> sut.createFromResume(1L, "BE", "제목", 10L, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("추출");
    }

    @Test
    @DisplayName("createFromResume - 존재하지 않는 portfolioResumeId → ResourceNotFoundException")
    void createFromResume_실패_존재하지않는_portfolioResumeId() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        resume.attachFile(new StoredFileRef("key", "resume.pdf", "application/pdf", 100L));
        resume.markExtracted("이력서 텍스트");
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));
        given(resumeRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sut.createFromResume(1L, "BE", "제목", 10L, 99L, null, List.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────

    private ResumeQuestion dummyQuestion() {
        com.bluehour.domain.resume.model.InterviewQuestion vo =
                new com.bluehour.domain.resume.model.InterviewQuestion(
                        "자기소개를 해주세요.", "지원자의 배경 파악", "경험, 기술스택", "간결하고 핵심만");
        return new ResumeQuestion(0, "자기소개", 90, vo);
    }

    private static void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
