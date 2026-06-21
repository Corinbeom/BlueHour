package com.bluehour.api.studyquiz.question;

import com.bluehour.api.studyquiz.question.dto.CreateCsQuizAttemptRequest;
import com.bluehour.domain.studyquiz.session.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CsQuizQuestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class CsQuizQuestionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CsQuizQuestionService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );
    }

    @Test
    @DisplayName("POST /api/cs-quiz-questions/{id}/attempts → 200")
    void submitAttempt_성공() throws Exception {
        CsQuizQuestion question = CsQuizQuestion.multipleChoice(
                0, CsQuizTopic.OS, CsQuizDifficulty.MID,
                "프로세스와 스레드의 차이?",
                List.of("A", "B", "C", "D"), 0, "참고 답안"
        );
        CsQuizFeedback feedback = new CsQuizFeedback(
                List.of("정답"), List.of(), "참고 답안", List.of());
        CsQuizAttempt attempt = question.addAttempt(null, 0, true, feedback);
        setId(attempt, 1L);
        setField(attempt, "createdAt", LocalDateTime.now());

        given(service.submitAttempt(eq(1L), eq(7L), any(CreateCsQuizAttemptRequest.class)))
                .willReturn(attempt);

        mockMvc.perform(post("/api/cs-quiz-questions/7/attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selectedChoiceIndex": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptId").value(1))
                .andExpect(jsonPath("$.data.correct").value(true));
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
