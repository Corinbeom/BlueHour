package com.bluehour.api.resume;

import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeFileType;
import com.bluehour.domain.resume.session.model.StoredFileRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class ResumeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ResumeService service;

    Resume resume;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );

        Member member = new Member("user@example.com");
        setId(member, 1L);

        resume = new Resume(member, "이력서.pdf", ResumeFileType.RESUME);
        setId(resume, 5L);
        resume.attachFile(new StoredFileRef("key123", "이력서.pdf", "application/pdf", 1024L));
    }

    @Test
    @DisplayName("POST /api/resumes multipart 업로드 → 200")
    void upload_성공() throws Exception {
        given(service.upload(eq(1L), any(), eq(ResumeFileType.RESUME), eq("이력서")))
                .willReturn(resume);

        MockMultipartFile file = new MockMultipartFile(
                "file", "이력서.pdf", "application/pdf", "pdf content".getBytes());

        mockMvc.perform(multipart("/api/resumes")
                        .file(file)
                        .param("fileType", "RESUME")
                        .param("title", "이력서"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("GET /api/resumes 목록 조회 → 200")
    void list_성공() throws Exception {
        given(service.listByMember(1L)).willReturn(List.of(resume));

        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("이력서.pdf"));
    }

    @Test
    @DisplayName("GET /api/resumes/{id} → 200")
    void get_성공() throws Exception {
        given(service.get(1L, 5L)).willReturn(resume);

        mockMvc.perform(get("/api/resumes/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    @DisplayName("DELETE /api/resumes/{id} → 200")
    void delete_성공() throws Exception {
        willDoNothing().given(service).delete(1L, 5L);

        mockMvc.perform(delete("/api/resumes/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
