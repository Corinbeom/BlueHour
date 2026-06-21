package com.bluehour.api.speechinterview;

import com.bluehour.domain.speechinterview.model.FeedbackStatus;
import com.bluehour.domain.speechinterview.port.SpeechInterviewSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class PendingFeedbackCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingFeedbackCleanupScheduler.class);
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);

    private final SpeechInterviewSessionRepository speechRepo;

    public PendingFeedbackCleanupScheduler(SpeechInterviewSessionRepository speechRepo) {
        this.speechRepo = speechRepo;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStalePendingFeedback() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(PENDING_TIMEOUT);

        speechRepo.findWithStalePendingAnswers(cutoff).forEach(session -> {
            session.getQuestions().forEach(question -> {
                var answer = question.getAnswer();
                if (answer == null || answer.getFeedbackStatus() != FeedbackStatus.PENDING) return;
                if (answer.getCreatedAt() == null || !answer.getCreatedAt().isBefore(cutoff)) return;

                long ageMinutes = Duration.between(answer.getCreatedAt(), now).toMinutes();
                answer.failFeedback();
                log.warn("PENDING expired: answerId={}, sessionId={}, age={}min",
                        answer.getId(), session.getId(), ageMinutes);
            });
            speechRepo.save(session);
        });
    }
}
