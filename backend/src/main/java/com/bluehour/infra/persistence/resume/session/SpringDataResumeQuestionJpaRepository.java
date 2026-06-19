package com.bluehour.infra.persistence.resume.session;

import com.bluehour.domain.resume.session.model.ResumeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataResumeQuestionJpaRepository extends JpaRepository<ResumeQuestion, Long> {
    Optional<ResumeQuestion> findByIdAndSession_Member_Id(Long id, Long memberId);
}
