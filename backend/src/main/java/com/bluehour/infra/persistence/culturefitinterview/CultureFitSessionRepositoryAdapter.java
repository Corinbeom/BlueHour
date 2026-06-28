package com.bluehour.infra.persistence.culturefitinterview;

import com.bluehour.domain.culturefitinterview.model.CultureFitSession;
import com.bluehour.domain.culturefitinterview.port.CultureFitSessionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CultureFitSessionRepositoryAdapter implements CultureFitSessionRepository {

    private final SpringDataCultureFitSessionRepository repo;

    public CultureFitSessionRepositoryAdapter(SpringDataCultureFitSessionRepository repo) {
        this.repo = repo;
    }

    @Override
    public CultureFitSession save(CultureFitSession session) {
        return repo.save(session);
    }

    @Override
    public Optional<CultureFitSession> findById(Long id) {
        return repo.findWithQuestionsById(id);
    }

    @Override
    public List<CultureFitSession> findByMemberIdOrderByCreatedAtDesc(Long memberId) {
        return repo.findAllByMemberIdOrderByCreatedAtDesc(memberId);
    }
}
