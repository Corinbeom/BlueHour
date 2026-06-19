package com.bluehour.domain.speechinterview.port;

import com.bluehour.domain.speechinterview.model.SpeechInterviewSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpeechInterviewSessionRepository {

    SpeechInterviewSession save(SpeechInterviewSession session);

    Optional<SpeechInterviewSession> findById(Long id);

    List<SpeechInterviewSession> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<SpeechInterviewSession> findWithStalePendingAnswers(LocalDateTime cutoff);
}
