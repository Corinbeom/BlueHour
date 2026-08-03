package com.bluehour.infra.persistence.culturefitinterview;

import com.bluehour.domain.culturefitinterview.model.CultureFitSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataCultureFitSessionRepository extends JpaRepository<CultureFitSession, Long> {

    @EntityGraph(attributePaths = {"questions"})
    Optional<CultureFitSession> findWithQuestionsById(Long id);

    @EntityGraph(attributePaths = {"questions"})
    List<CultureFitSession> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
