package com.bluehour.infra.persistence.studyquiz.session;

import com.bluehour.domain.studyquiz.session.model.CsQuizQuestion;
import com.bluehour.domain.studyquiz.session.port.CsQuizQuestionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CsQuizQuestionRepositoryAdapter implements CsQuizQuestionRepository {

    private final SpringDataCsQuizQuestionJpaRepository repo;

    public CsQuizQuestionRepositoryAdapter(SpringDataCsQuizQuestionJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public CsQuizQuestion save(CsQuizQuestion question) {
        return repo.save(question);
    }

    @Override
    public Optional<CsQuizQuestion> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Optional<CsQuizQuestion> findByIdAndSessionMemberId(Long id, Long memberId) {
        return repo.findByIdAndSession_Member_Id(id, memberId);
    }
}
