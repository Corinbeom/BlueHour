package com.bluehour.api.studyquiz.session;

import com.bluehour.api.studyquiz.session.dto.CsQuizSessionResponse;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.studyquiz.session.model.CsQuizDifficulty;
import com.bluehour.domain.studyquiz.session.model.CsQuizSession;
import com.bluehour.domain.studyquiz.session.model.CsQuizSessionStatus;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CsQuizSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class CsQuizSessionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CsQuizSessionService service;

    CsQuizSession session;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );

        Member member = new Member("user@example.com");
        setId(member, 1L);

        Set<CsQuizTopic> topics = new LinkedHashSet<>();
        topics.add(CsQuizTopic.OS);
        session = new CsQuizSession(member, "CS Quiz (MID)", CsQuizDifficulty.MID, topics);
        setId(session, 30L);
        setField(session, "createdAt", LocalDateTime.now());
        setField(session, "updatedAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /api/cs-quiz-sessions → 200")
    void listByCurrentMember_성공() throws Exception {
        CsQuizSessionResponse dto = new CsQuizSessionResponse(
                30L, "CS Quiz (MID)", "MID", Set.of("OS"),
                CsQuizSessionStatus.CREATED, List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(service.listByMemberCached(1L)).willReturn(List.of(dto));

        mockMvc.perform(get("/api/cs-quiz-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("CS Quiz (MID)"));
    }

    @Test
    @DisplayName("POST /api/cs-quiz-sessions → 200")
    void create_성공() throws Exception {
        given(service.create(eq(1L), eq("MID"), eq(List.of("OS")), eq(5), eq("퀴즈 제목")))
                .willReturn(session);

        mockMvc.perform(post("/api/cs-quiz-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "difficulty": "MID",
                                  "topics": ["OS"],
                                  "questionCount": 5,
                                  "title": "퀴즈 제목"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(30));
    }

    @Test
    @DisplayName("GET /api/cs-quiz-sessions/{id} → 200")
    void get_성공() throws Exception {
        given(service.get(30L, 1L)).willReturn(session);

        mockMvc.perform(get("/api/cs-quiz-sessions/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.title").value("CS Quiz (MID)"));
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
