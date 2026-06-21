package com.bluehour.infra.persistence.speechinterview;

import com.bluehour.domain.speechinterview.model.SpeechInterviewSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpringDataSpeechInterviewSessionRepository extends JpaRepository<SpeechInterviewSession, Long> {

    @EntityGraph(attributePaths = {"questions", "questions.answer"})
    Optional<SpeechInterviewSession> findWithQuestionsById(Long id);

    @EntityGraph(attributePaths = {"questions"})
    List<SpeechInterviewSession> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    @EntityGraph(attributePaths = {"questions", "questions.answer"})
    @Query("""
            SELECT DISTINCT s
            FROM SpeechInterviewSession s
            JOIN s.questions q
            JOIN q.answer a
            WHERE a.feedbackStatus = com.bluehour.domain.speechinterview.model.FeedbackStatus.PENDING
              AND a.createdAt < :cutoff
            """)
    List<SpeechInterviewSession> findWithStalePendingAnswers(@Param("cutoff") LocalDateTime cutoff);
}
