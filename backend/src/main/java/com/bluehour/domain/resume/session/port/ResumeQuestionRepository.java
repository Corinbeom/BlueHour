package com.bluehour.domain.resume.session.port;

import com.bluehour.domain.resume.session.model.ResumeQuestion;

import java.util.Optional;

public interface ResumeQuestionRepository {
    ResumeQuestion save(ResumeQuestion question);

    Optional<ResumeQuestion> findById(Long id);

    Optional<ResumeQuestion> findByIdAndSessionMemberId(Long id, Long memberId);
}
