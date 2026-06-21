package com.bluehour.domain.coach.port;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface CoachAiPort {

    GeneratedCoachAnalysis analyzeReadiness(CoachContext context);

    record CoachContext(
            List<String> targetRoles,
            String roleCategory,
            boolean technicalTrack,
            int totalApplications,
            Map<String, Integer> statusCounts,
            int resumeCount,
            int daysSinceLastAnalysis,
            int interviewCompleted,
            int interviewTotal,
            Map<String, Double> quizAccuracy,
            int quizTotalAttempts
    ) implements Serializable {}

    record GeneratedCoachAnalysis(
            int score,
            String primary,
            List<String> strengths,
            List<String> gaps,
            List<PlanItem> plan,
            String today
    ) implements Serializable {}

    record PlanItem(
            int d,
            @JsonProperty("do")
            String do_
    ) implements Serializable {}
}
