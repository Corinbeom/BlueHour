package com.bluehour.api.resume.question;

import com.bluehour.domain.resume.model.InterviewQuestion;
import com.bluehour.domain.resume.session.model.Feedback;
import com.bluehour.domain.resume.session.model.ResumeAnswerAttempt;
import com.bluehour.domain.resume.session.model.ResumeQuestion;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeQuestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class ResumeQuestionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ResumeQuestionService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );
    }

    @Test
    @DisplayName("POST /api/resume-questions/{id}/feedback 정상 → 200")
    void feedback_성공() throws Exception {
        InterviewQuestion iq = new InterviewQuestion("질문?", "의도", "키워드", "모범답안");
        ResumeQuestion question = new ResumeQuestion(0, "기술", 80, iq);
        Feedback feedback = new Feedback(
                List.of("잘했어요"), List.of("보완점"), "추천 답변", List.of("후속 질문"));
        ResumeAnswerAttempt attempt = question.addAttempt("제 답변입니다", feedback);
        setId(attempt, 1L);
        setField(attempt, "createdAt", LocalDateTime.now());

        given(service.createFeedback(eq(1L), eq(5L), eq("제 답변입니다"), any())).willReturn(attempt);

        mockMvc.perform(post("/api/resume-questions/5/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerText": "제 답변입니다"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptId").value(1))
                .andExpect(jsonPath("$.data.strengths[0]").value("잘했어요"));
    }

    @Test
    @DisplayName("POST /api/resume-questions/{id}/feedback 유효성 실패 (빈 answerText) → 400")
    void feedback_유효성_실패() throws Exception {
        mockMvc.perform(post("/api/resume-questions/5/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answerText": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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
