package com.bluehour.api.coach.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CoachSummaryResponse(
        List<String> targetRoles,
        String inferredFrom,
        String roleCategory,
        boolean technicalTrack,
        Recruitment recruitment,
        Resume resume,
        Interview interview,
        Quiz quiz
) implements Serializable {

    public record Recruitment(
            int totalApplications,
            Map<String, Integer> statusBreakdown,
            List<String> recentJdTitles
    ) implements Serializable {}

    public record Resume(
            int uploadedCount,
            LocalDateTime lastAnalyzedAt
    ) implements Serializable {}

    public record Interview(
            int totalSessions,
            int completedSessions,
            double averageTurns
    ) implements Serializable {}

    public record Quiz(
            int totalAttempts,
            Map<String, Double> topicAccuracy,
            Map<String, Integer> topicAttempts
    ) implements Serializable {}
}
