package com.bluehour.domain.assistant.port;

import java.util.List;
import java.util.Map;

public record AssistantContext(
        String displayName,
        List<String> targetRoles,
        RecruitmentSnapshot recruitment,
        ResumeSnapshot resume,
        InterviewSnapshot interview,
        List<ResumeSessionSummary> resumeSessions,
        QuizSnapshot quiz
) {
    public record RecruitmentSnapshot(
            int totalApplications,
            Map<String, Integer> statusBreakdown,
            List<String> recentTitles
    ) {
    }

    public record ResumeSnapshot(
            int resumeCount,
            int daysSinceLastAnalysis
    ) {
    }

    public record InterviewSnapshot(
            int totalSessions,
            int completedSessions,
            double averageTurns
    ) {
    }

    public record ResumeSessionSummary(
            String positionType,
            String status,
            String completedAt,
            int questionCount
    ) {
    }

    public record QuizSnapshot(
            int totalAttempts,
            List<TopicAccuracy> topicAccuracy
    ) {
    }

    public record TopicAccuracy(
            String topic,
            int attempts,
            double accuracy
    ) {
    }
}
