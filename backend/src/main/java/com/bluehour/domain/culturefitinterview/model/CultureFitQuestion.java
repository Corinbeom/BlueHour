package com.bluehour.domain.culturefitinterview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "culture_fit_questions")
public class CultureFitQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CultureFitSession session;

    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 100)
    private String badge;

    @Column(nullable = false)
    private int likelihood;

    @Column(nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(columnDefinition = "text")
    private String intention;

    @Column(length = 500)
    private String keywords;

    @Column(columnDefinition = "text")
    private String modelAnswer;

    @Column(columnDefinition = "text")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CultureFitFeedbackStatus feedbackStatus;

    @Embedded
    private CultureFitFeedback feedback;

    protected CultureFitQuestion() {
    }

    public CultureFitQuestion(
            int orderIndex,
            String badge,
            int likelihood,
            String questionText,
            String intention,
            String keywords,
            String modelAnswer
    ) {
        if (questionText == null || questionText.isBlank()) throw new IllegalArgumentException("questionText는 필수입니다.");
        this.orderIndex = orderIndex;
        this.badge = (badge == null || badge.isBlank()) ? "컬처핏" : badge;
        this.likelihood = Math.max(0, Math.min(100, likelihood));
        this.questionText = questionText;
        this.intention = intention;
        this.keywords = keywords;
        this.modelAnswer = modelAnswer;
        this.feedbackStatus = CultureFitFeedbackStatus.PENDING;
    }

    void attachTo(CultureFitSession session) {
        if (session == null) throw new IllegalArgumentException("session은 필수입니다.");
        this.session = session;
    }

    public void submitAnswer(String answerText) {
        if (answerText == null || answerText.isBlank()) throw new IllegalArgumentException("answerText는 필수입니다.");
        this.answerText = answerText;
        this.feedbackStatus = CultureFitFeedbackStatus.PENDING;
        this.feedback = null;
    }

    public void completeFeedback(CultureFitFeedback feedback) {
        if (feedback == null) throw new IllegalArgumentException("feedback은 필수입니다.");
        this.feedback = feedback;
        this.feedbackStatus = CultureFitFeedbackStatus.COMPLETED;
    }

    public void failFeedback() {
        this.feedbackStatus = CultureFitFeedbackStatus.FAILED;
    }

    public Long getId() { return id; }
    public CultureFitSession getSession() { return session; }
    public int getOrderIndex() { return orderIndex; }
    public String getBadge() { return badge; }
    public int getLikelihood() { return likelihood; }
    public String getQuestionText() { return questionText; }
    public String getIntention() { return intention; }
    public String getKeywords() { return keywords; }
    public String getModelAnswer() { return modelAnswer; }
    public String getAnswerText() { return answerText; }
    public CultureFitFeedbackStatus getFeedbackStatus() { return feedbackStatus; }
    public CultureFitFeedback getFeedback() { return feedback; }
}
