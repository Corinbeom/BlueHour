package com.bluehour.domain.culturefitinterview.port;

import com.bluehour.domain.culturefitinterview.model.CultureFitSession;

import java.util.List;
import java.util.Optional;

public interface CultureFitSessionRepository {
    CultureFitSession save(CultureFitSession session);
    Optional<CultureFitSession> findById(Long id);
    List<CultureFitSession> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
