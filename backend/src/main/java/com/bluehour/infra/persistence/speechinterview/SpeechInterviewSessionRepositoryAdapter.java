package com.bluehour.infra.persistence.speechinterview;

import com.bluehour.domain.speechinterview.model.SpeechInterviewSession;
import com.bluehour.domain.speechinterview.port.SpeechInterviewSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SpeechInterviewSessionRepositoryAdapter implements SpeechInterviewSessionRepository {

    private final SpringDataSpeechInterviewSessionRepository repo;

    public SpeechInterviewSessionRepositoryAdapter(SpringDataSpeechInterviewSessionRepository repo) {
        this.repo = repo;
    }

    @Override
    public SpeechInterviewSession save(SpeechInterviewSession session) {
        return repo.save(session);
    }

    @Override
    public Optional<SpeechInterviewSession> findById(Long id) {
        return repo.findWithQuestionsById(id);
    }

    @Override
    public List<SpeechInterviewSession> findByMemberIdOrderByCreatedAtDesc(Long memberId) {
        return repo.findAllByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Override
    public List<SpeechInterviewSession> findWithStalePendingAnswers(LocalDateTime cutoff) {
        return repo.findWithStalePendingAnswers(cutoff);
    }
}
