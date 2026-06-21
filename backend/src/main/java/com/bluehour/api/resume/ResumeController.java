package com.bluehour.api.resume;

import com.bluehour.api.resume.dto.ResumeResponse;
import com.bluehour.common.ApiResponse;
import com.bluehour.common.AuthUtils;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeFileType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "이력서 파일", description = "이력서/포트폴리오 파일 업로드 및 관리")
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @Operation(summary = "이력서 업로드", description = "이력서 또는 포트폴리오 파일을 업로드합니다. (최대 10MB)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String fileType,
            @RequestParam(required = false) String title
    ) {
        Long memberId = AuthUtils.currentMemberId();
        ResumeFileType type = ResumeFileType.valueOf(fileType.toUpperCase());
        Resume resume = service.upload(memberId, file, type, title);
        return ApiResponse.success(ResumeResponse.from(resume));
    }

    @Operation(summary = "내 이력서 목록 조회", description = "로그인한 사용자의 업로드된 이력서 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<ResumeResponse>> list() {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(
                service.listByMember(memberId).stream()
                        .map(ResumeResponse::from)
                        .toList()
        );
    }

    @Operation(summary = "이력서 조회", description = "ID로 이력서 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ApiResponse<ResumeResponse> get(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        return ApiResponse.success(ResumeResponse.from(service.get(memberId, id)));
    }

    @Operation(summary = "이력서 파일 다운로드/미리보기", description = "이력서 파일 바이너리를 반환합니다. (Content-Disposition: inline)")
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getFile(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        ResumeService.FileData data = service.serveFile(memberId, id);
        String contentType = data.contentType() != null ? data.contentType() : "application/octet-stream";
        String filename = data.filename() != null ? data.filename() : "file";
        String encodedFilename = new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encodedFilename + "\"")
                .body(data.bytes());
    }

    @Operation(summary = "이력서 삭제", description = "이력서 파일과 메타데이터를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long memberId = AuthUtils.currentMemberId();
        service.delete(memberId, id);
        return ApiResponse.ok();
    }
}
