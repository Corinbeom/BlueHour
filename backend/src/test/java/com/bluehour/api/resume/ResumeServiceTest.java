package com.bluehour.api.resume;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeExtractStatus;
import com.bluehour.domain.resume.model.ResumeFileType;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.resume.session.model.StoredFileRef;
import com.bluehour.domain.resume.session.port.FileStoragePort;
import com.bluehour.domain.resume.session.port.TextExtractorPort;
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
class ResumeServiceTest {

    @Mock ResumeRepository resumeRepository;
    @Mock MemberRepository memberRepository;
    @Mock FileStoragePort fileStorage;
    @Mock TextExtractorPort textExtractor;

    @InjectMocks ResumeService sut;

    private Member member;
    private MockMultipartFile validFile;

    @BeforeEach
    void setUp() {
        member = new Member("test@example.com");
        setId(member, 1L);
        validFile = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "PDF 내용".getBytes()
        );
    }

    // ─────────────────────────────────────────────
    // upload 정상 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("upload 성공 - 텍스트 추출 성공 시 EXTRACTED 상태")
    void upload_성공_텍스트추출_성공() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        StoredFileRef ref = new StoredFileRef("key", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(ref);
        given(textExtractor.extract(any(), anyString())).willReturn("추출된 텍스트");
        given(resumeRepository.save(any(Resume.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Resume result = sut.upload(1L, validFile, ResumeFileType.RESUME, "내 이력서");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExtractStatus()).isEqualTo(ResumeExtractStatus.EXTRACTED);
        assertThat(result.getExtractedText()).isEqualTo("추출된 텍스트");
        assertThat(result.getTitle()).isEqualTo("내 이력서");
    }

    @Test
    @DisplayName("upload 성공 - 텍스트 추출 실패 시 FAILED 상태 (예외 안 터짐)")
    void upload_성공_텍스트추출_실패시_FAILED() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        StoredFileRef ref = new StoredFileRef("key", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(ref);
        given(textExtractor.extract(any(), anyString())).willThrow(new RuntimeException("파싱 실패"));
        given(resumeRepository.save(any(Resume.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Resume result = sut.upload(1L, validFile, ResumeFileType.RESUME, "이력서");

        // then
        assertThat(result.getExtractStatus()).isEqualTo(ResumeExtractStatus.FAILED);
        assertThat(result.getExtractedText()).isNull();
    }

    @Test
    @DisplayName("upload 성공 - title null이면 파일명 사용")
    void upload_성공_title_null이면_파일명사용() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        StoredFileRef ref = new StoredFileRef("key", "resume.pdf", "application/pdf", 100L);
        given(fileStorage.save(any(), anyString(), anyString())).willReturn(ref);
        given(textExtractor.extract(any(), anyString())).willReturn("텍스트");
        given(resumeRepository.save(any(Resume.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        Resume result = sut.upload(1L, validFile, ResumeFileType.RESUME, null);

        // then
        assertThat(result.getTitle()).isEqualTo("resume.pdf");
    }

    // ─────────────────────────────────────────────
    // upload 실패 케이스
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("upload 실패 - 파일 null")
    void upload_실패_파일_null() {
        assertThatThrownBy(() -> sut.upload(1L, null, ResumeFileType.RESUME, "제목"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일");
    }

    @Test
    @DisplayName("upload 실패 - 파일 비어있음")
    void upload_실패_파일_비어있음() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        assertThatThrownBy(() -> sut.upload(1L, emptyFile, ResumeFileType.RESUME, "제목"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일");
    }

    @Test
    @DisplayName("upload 실패 - 10MB 초과")
    void upload_실패_10MB초과() {
        byte[] bigContent = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", bigContent
        );

        assertThatThrownBy(() -> sut.upload(1L, bigFile, ResumeFileType.RESUME, "제목"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("upload 실패 - 존재하지 않는 memberId")
    void upload_실패_존재하지않는_memberId() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.upload(99L, validFile, ResumeFileType.RESUME, "제목"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─────────────────────────────────────────────
    // get 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("get 성공")
    void get_성공() {
        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        given(resumeRepository.findById(1L)).willReturn(Optional.of(resume));

        Resume result = sut.get(1L);

        assertThat(result).isEqualTo(resume);
    }

    @Test
    @DisplayName("get 실패 - 존재하지 않는 id")
    void get_실패_존재하지않는_id() {
        given(resumeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.get(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─────────────────────────────────────────────
    // delete 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("delete 성공")
    void delete_성공() {
        Resume resume = new Resume(member, "이력서", ResumeFileType.RESUME);
        // member의 id를 설정하기 위해 reflection 사용
        setId(member, 1L);
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        // when
        sut.delete(1L, 10L);

        // then
        then(resumeRepository).should().delete(resume);
    }

    @Test
    @DisplayName("delete 실패 - 존재하지 않는 id")
    void delete_실패_존재하지않는_id() {
        given(resumeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.delete(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("delete 실패 - 타인 파일 삭제 시도")
    void delete_실패_타인파일() {
        Member otherMember = new Member("other@example.com");
        setId(otherMember, 2L);
        Resume resume = new Resume(otherMember, "이력서", ResumeFileType.RESUME);
        given(resumeRepository.findById(10L)).willReturn(Optional.of(resume));

        assertThatThrownBy(() -> sut.delete(1L, 10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("본인");
    }

    // ─────────────────────────────────────────────
    // listByMember 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("listByMember 조회 성공")
    void listByMember_성공() {
        Resume r1 = new Resume(member, "이력서1", ResumeFileType.RESUME);
        Resume r2 = new Resume(member, "포트폴리오1", ResumeFileType.PORTFOLIO);
        given(resumeRepository.findAllByMemberId(1L)).willReturn(List.of(r1, r2));

        List<Resume> result = sut.listByMember(1L);

        assertThat(result).hasSize(2);
    }

    // ─────────────────────────────────────────────
    // 헬퍼 메서드
    // ─────────────────────────────────────────────

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
