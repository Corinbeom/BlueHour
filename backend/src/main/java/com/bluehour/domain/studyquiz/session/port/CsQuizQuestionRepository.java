package com.bluehour.domain.studyquiz.session.port;

import com.bluehour.domain.studyquiz.session.model.CsQuizQuestion;

import java.util.Optional;

public interface CsQuizQuestionRepository {
    CsQuizQuestion save(CsQuizQuestion question);

    Optional<CsQuizQuestion> findById(Long id);

    Optional<CsQuizQuestion> findByIdAndSessionMemberId(Long id, Long memberId);
}
