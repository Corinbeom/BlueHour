package com.bluehour.domain.culturefitinterview.model;

import com.bluehour.domain.member.model.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "culture_fit_sessions")
public class CultureFitSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 120)
    private String companyName;

    @Column(nullable = false, columnDefinition = "text")
    private String companyCultureText;

    @Column(columnDefinition = "text")
    private String jobDescriptionText;

    @Column(length = 50)
    private String positionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CultureFitSessionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CultureFitQuestion> questions = new ArrayList<>();

    protected CultureFitSession() {
    }

    public CultureFitSession(Member member, String companyName, String companyCultureText, String jobDescriptionText, String positionType) {
        if (member == null) throw new IllegalArgumentException("member는 필수입니다.");
        if (companyCultureText == null || companyCultureText.isBlank()) {
            throw new IllegalArgumentException("companyCultureText는 필수입니다.");
        }
        this.member = member;
        this.companyName = normalizeBlank(companyName);
        this.companyCultureText = companyCultureText;
        this.jobDescriptionText = normalizeBlank(jobDescriptionText);
        this.positionType = normalizeBlank(positionType);
        this.status = CultureFitSessionStatus.CREATED;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void addQuestion(CultureFitQuestion question) {
        question.attachTo(this);
        this.questions.add(question);
    }

    public void markInProgress() {
        if (this.status == CultureFitSessionStatus.CREATED) {
            this.status = CultureFitSessionStatus.IN_PROGRESS;
        }
    }

    public void complete() {
        if (this.status == CultureFitSessionStatus.COMPLETED) return;
        boolean hasIncomplete = questions.stream()
                .anyMatch(q -> q.getFeedbackStatus() != CultureFitFeedbackStatus.COMPLETED);
        if (hasIncomplete) {
            throw new IllegalStateException("아직 완료되지 않은 피드백이 있습니다.");
        }
        this.status = CultureFitSessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getCompanyName() { return companyName; }
    public String getCompanyCultureText() { return companyCultureText; }
    public String getJobDescriptionText() { return jobDescriptionText; }
    public String getPositionType() { return positionType; }
    public CultureFitSessionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public List<CultureFitQuestion> getQuestions() { return Collections.unmodifiableList(questions); }
}
