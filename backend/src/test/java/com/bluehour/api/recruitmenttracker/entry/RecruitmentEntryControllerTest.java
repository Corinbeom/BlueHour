package com.bluehour.api.recruitmenttracker.entry;

import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.recruitmenttracker.entry.model.PlatformType;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecruitmentEntryController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class RecruitmentEntryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RecruitmentEntryService service;

    Member member;
    RecruitmentEntry entry;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );

        member = new Member("user@example.com");
        setId(member, 1L);

        entry = new RecruitmentEntry(member, "카카오", "백엔드", RecruitmentStep.APPLIED,
                PlatformType.MANUAL, null, LocalDate.of(2024, 1, 15));
        setId(entry, 10L);
    }

    @Test
    @DisplayName("POST /api/recruitment-entries 생성 성공 → 200")
    void create_성공() throws Exception {
        given(service.create(eq(1L), eq("카카오"), eq("백엔드"), eq(RecruitmentStep.APPLIED),
                eq(PlatformType.MANUAL), isNull(), any())).willReturn(entry);

        mockMvc.perform(post("/api/recruitment-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "카카오",
                                  "position": "백엔드",
                                  "step": "APPLIED",
                                  "platformType": "MANUAL",
                                  "appliedDate": "2024-01-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.companyName").value("카카오"));
    }

    @Test
    @DisplayName("GET /api/recruitment-entries/{id} 조회 → 200")
    void get_성공() throws Exception {
        given(service.get(10L, 1L)).willReturn(entry);

        mockMvc.perform(get("/api/recruitment-entries/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.companyName").value("카카오"));
    }

    @Test
    @DisplayName("GET /api/recruitment-entries/by-member/me → 200")
    void listByCurrentMember_성공() throws Exception {
        given(service.listByMember(1L)).willReturn(List.of(entry));

        mockMvc.perform(get("/api/recruitment-entries/by-member/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].companyName").value("카카오"));
    }

    @Test
    @DisplayName("PUT /api/recruitment-entries/{id} 수정 → 200")
    void update_성공() throws Exception {
        given(service.update(eq(10L), eq(1L), eq("네이버"), eq("프론트엔드"), any(), any(), any(), any()))
                .willReturn(entry);

        mockMvc.perform(put("/api/recruitment-entries/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "네이버",
                                  "position": "프론트엔드",
                                  "step": "INTERVIEWING",
                                  "platformType": "WANTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/recruitment-entries/{id}/step → 200")
    void changeStep_성공() throws Exception {
        given(service.changeStep(10L, 1L, RecruitmentStep.OFFERED)).willReturn(entry);

        mockMvc.perform(patch("/api/recruitment-entries/10/step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"step": "OFFERED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/recruitment-entries/{id} → 200")
    void delete_성공() throws Exception {
        willDoNothing().given(service).delete(10L, 1L);

        mockMvc.perform(delete("/api/recruitment-entries/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
