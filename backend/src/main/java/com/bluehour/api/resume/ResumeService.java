package com.bluehour.api.resume;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeFileType;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.resume.session.model.StoredFileRef;
import com.bluehour.domain.resume.session.port.FileStoragePort;
import com.bluehour.domain.resume.session.port.TextExtractorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.event.EventListener;
import com.bluehour.domain.member.event.MemberDeletedEvent;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    public record FileData(byte[] bytes, String contentType, String filename) {}

    private final ResumeRepository resumeRepository;
    private final MemberRepository memberRepository;
    private final FileStoragePort fileStorage;
    private final TextExtractorPort textExtractor;

    public ResumeService(
            ResumeRepository resumeRepository,
            MemberRepository memberRepository,
            FileStoragePort fileStorage,
            TextExtractorPort textExtractor
    ) {
        this.resumeRepository = resumeRepository;
        this.memberRepository = memberRepository;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
    }

    public Resume upload(Long memberId, MultipartFile file, ResumeFileType fileType, String title) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일은 필수입니다.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("파일 크기는 최대 10MB 입니다.");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        String resolvedTitle = (title == null || title.isBlank())
                ? (file.getOriginalFilename() != null ? file.getOriginalFilename() : "untitled")
                : title;

        Resume resume = new Resume(member, resolvedTitle, fileType);

        byte[] bytes = readBytes(file);
        StoredFileRef storedFile;
        try {
            storedFile = fileStorage.save(bytes, file.getOriginalFilename(), file.getContentType());
        } catch (Exception e) {
            log.error("[ResumeService] 파일 저장(S3) 실패: filename={}, error={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("파일 저장에 실패했습니다. 잠시 후 다시 시도해주세요.", e);
        }
        resume.attachFile(storedFile);

        try {
            String extractedText = textExtractor.extract(bytes, file.getContentType());
            resume.markExtracted(extractedText);
        } catch (Exception e) {
            resume.markExtractFailed();
        }

        return resumeRepository.save(resume);
    }

    @Transactional(readOnly = true)
    public List<Resume> listByMember(Long memberId) {
        return resumeRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Resume get(Long id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume를 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public Resume get(Long memberId, Long id) {
        Resume resume = get(id);
        ensureOwner(resume, memberId);
        return resume;
    }

    @Transactional(readOnly = true)
    public FileData serveFile(Long memberId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume를 찾을 수 없습니다. id=" + resumeId));
        if (!resume.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인의 파일만 조회할 수 있습니다.");
        }
        StoredFileRef stored = resume.getStoredFile();
        if (stored == null || stored.getStorageKey() == null) {
            throw new ResourceNotFoundException("파일이 존재하지 않습니다.");
        }
        byte[] bytes = fileStorage.load(stored.getStorageKey());
        return new FileData(bytes, stored.getContentType(), stored.getOriginalFilename());
    }

    public void delete(Long memberId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume를 찾을 수 없습니다. id=" + resumeId));
        if (!resume.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인의 파일만 삭제할 수 있습니다.");
        }
        String storageKey = resume.getStoredFile() != null ? resume.getStoredFile().getStorageKey() : null;
        resumeRepository.delete(resume);
        if (storageKey != null) {
            try {
                fileStorage.delete(storageKey);
            } catch (Exception e) {
                // S3 삭제 실패는 non-fatal — DB 레코드는 이미 삭제됨
                log.warn("[ResumeService] S3 파일 삭제 실패 (DB 레코드는 삭제됨): key={}, error={}", storageKey, e.getMessage());
            }
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.", e);
        }
    }

    private void ensureOwner(Resume resume, Long memberId) {
        if (!resume.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인의 파일만 조회할 수 있습니다.");
        }
    }

    @EventListener
    public void onMemberDeleted(MemberDeletedEvent event) {
        listByMember(event.memberId()).forEach(resume -> delete(event.memberId(), resume.getId()));
    }
}
