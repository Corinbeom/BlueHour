package com.bluehour.infra.persistence.studyquiz.session;

import com.bluehour.domain.studyquiz.session.model.CsQuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataCsQuizQuestionJpaRepository extends JpaRepository<CsQuizQuestion, Long> {
    Optional<CsQuizQuestion> findByIdAndSession_Member_Id(Long id, Long memberId);
}
