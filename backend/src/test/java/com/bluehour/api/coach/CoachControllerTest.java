package com.bluehour.api.coach;

import com.bluehour.api.coach.dto.CoachAnalysisResponse;
import com.bluehour.api.coach.dto.CoachSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "bluehour.frontend-url=http://localhost:3000")
class CoachControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CoachService coachService;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of())
        );
    }

    @Test
    @DisplayName("GET /api/coach/summary → 200")
    void summary_성공() throws Exception {
        given(coachService.getSummary(1L)).willReturn(new CoachSummaryResponse(
                List.of("백엔드 개발자"),
                "TARGET_ROLES",
                "DEVELOPER",
                true,
                new CoachSummaryResponse.Recruitment(1, Map.of("APPLIED", 1), List.of("백엔드 개발자")),
                new CoachSummaryResponse.Resume(0, null),
                new CoachSummaryResponse.Interview(0, 0, 0.0),
                new CoachSummaryResponse.Quiz(0, Map.of(), Map.of())
        ));

        mockMvc.perform(get("/api/coach/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetRoles[0]").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.inferredFrom").value("TARGET_ROLES"))
                .andExpect(jsonPath("$.data.roleCategory").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.technicalTrack").value(true));
    }

    @Test
    @DisplayName("GET /api/coach/analysis → 200")
    void analysis_성공() throws Exception {
        given(coachService.getAnalysis(1L)).willReturn(new CoachAnalysisResponse(
                70,
                "백엔드 개발자",
                List.of("지원 시작"),
                List.of("면접 부족"),
                List.of(new CoachAnalysisResponse.PlanItem(1, "면접 연습 1회")),
                "면접 연습 1회",
                false
        ));

        mockMvc.perform(get("/api/coach/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.score").value(70))
                .andExpect(jsonPath("$.data.plan[0].do").value("면접 연습 1회"));
    }

    @Test
    @DisplayName("POST /api/coach/refresh → 200")
    void refresh_성공() throws Exception {
        given(coachService.refresh(1L)).willReturn(CoachAnalysisResponse.forMissingTargetRoles());

        mockMvc.perform(post("/api/coach/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.needsTargetRoles").value(true));
    }
}
