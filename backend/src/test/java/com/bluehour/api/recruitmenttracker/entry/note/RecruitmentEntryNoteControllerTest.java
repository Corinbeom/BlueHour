package com.bluehour.api.recruitmenttracker.entry.note;

import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.recruitmenttracker.entry.model.PlatformType;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentStep;
import com.bluehour.domain.recruitmenttracker.note.model.RecruitmentEntryNote;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecruitmentEntryNoteController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class RecruitmentEntryNoteControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RecruitmentEntryNoteService service;

    RecruitmentEntry entry;
    RecruitmentEntryNote note;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );

        Member member = new Member("user@example.com");
        setId(member, 1L);

        entry = new RecruitmentEntry(member, "카카오", "백엔드", RecruitmentStep.APPLIED,
                PlatformType.MANUAL, null);
        setId(entry, 10L);

        note = new RecruitmentEntryNote(entry, "면접 준비할 것");
        setId(note, 100L);
        setField(note, "createdAt", Instant.now());
        setField(note, "updatedAt", Instant.now());
    }

    @Test
    @DisplayName("GET /api/recruitment-entries/{entryId}/notes → 200")
    void list_성공() throws Exception {
        given(service.list(10L, 1L)).willReturn(List.of(note));

        mockMvc.perform(get("/api/recruitment-entries/10/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].content").value("면접 준비할 것"));
    }

    @Test
    @DisplayName("POST /api/recruitment-entries/{entryId}/notes → 200")
    void create_성공() throws Exception {
        given(service.create(eq(10L), eq(1L), eq("새 메모"))).willReturn(note);

        mockMvc.perform(post("/api/recruitment-entries/10/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "새 메모"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/recruitment-entries/{entryId}/notes/{noteId} → 200")
    void update_성공() throws Exception {
        given(service.update(eq(10L), eq(1L), eq(100L), eq("수정된 메모"))).willReturn(note);

        mockMvc.perform(put("/api/recruitment-entries/10/notes/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "수정된 메모"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/recruitment-entries/{entryId}/notes/{noteId} → 200")
    void delete_성공() throws Exception {
        willDoNothing().given(service).delete(10L, 1L, 100L);

        mockMvc.perform(delete("/api/recruitment-entries/10/notes/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void setField(Object entity, String fieldName, Object value) throws Exception {
        Field field = entity.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, value);
    }
}
