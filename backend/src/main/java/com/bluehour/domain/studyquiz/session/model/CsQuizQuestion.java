package com.bluehour.domain.studyquiz.session.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "cs_quiz_questions")
public class CsQuizQuestion {

    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CsQuizSession session;

    @Column(nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CsQuizTopic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CsQuizDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CsQuizQuestionType type;

    @Lob
    @Column(nullable = false)
    private String prompt;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "cs_quiz_question_choices", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "idx")
    @Column(name = "choice_text", length = 2000)
    private List<String> choices = new ArrayList<>();

    @Column(name = "correct_choice_index")
    private Integer correctChoiceIndex;

    @Lob
    @Column(name = "reference_answer")
    private String referenceAnswer;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "cs_quiz_question_rubric_keywords", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "idx")
    @Column(name = "keyword", length = 200)
    private List<String> rubricKeywords = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<CsQuizAttempt> attempts = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected CsQuizQuestion() {
    }

    public static CsQuizQuestion multipleChoice(
            int orderIndex,
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String prompt,
            List<String> choices,
            int correctChoiceIndex,
            String referenceAnswer
    ) {
        if (choices == null || choices.size() < 2) throw new IllegalArgumentException("choices는 2개 이상이어야 합니다.");
        if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
            throw new IllegalArgumentException("correctChoiceIndex 범위가 올바르지 않습니다.");
        }
        CsQuizQuestion q = new CsQuizQuestion(orderIndex, topic, difficulty, CsQuizQuestionType.MULTIPLE_CHOICE, prompt);
        q.choices = new ArrayList<>(choices);
        q.correctChoiceIndex = correctChoiceIndex;
        q.referenceAnswer = referenceAnswer;
        return q;
    }

    public static CsQuizQuestion shortAnswer(
            int orderIndex,
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String prompt,
            List<String> rubricKeywords,
            String referenceAnswer
    ) {
        CsQuizQuestion q = new CsQuizQuestion(orderIndex, topic, difficulty, CsQuizQuestionType.SHORT_ANSWER, prompt);
        if (rubricKeywords != null) q.rubricKeywords = new ArrayList<>(rubricKeywords);
        q.referenceAnswer = referenceAnswer;
        return q;
    }

    private CsQuizQuestion(int orderIndex, CsQuizTopic topic, CsQuizDifficulty difficulty, CsQuizQuestionType type, String prompt) {
        if (topic == null) throw new IllegalArgumentException("topic은 필수입니다.");
        if (difficulty == null) throw new IllegalArgumentException("difficulty는 필수입니다.");
        if (type == null) throw new IllegalArgumentException("type은 필수입니다.");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt는 필수입니다.");
        this.orderIndex = orderIndex;
        this.topic = topic;
        this.difficulty = difficulty;
        this.type = type;
        this.prompt = prompt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    void attachTo(CsQuizSession session) {
        if (session == null) throw new IllegalArgumentException("session은 필수입니다.");
        this.session = session;
    }

    public boolean canAttempt() {
        return attempts.size() < MAX_ATTEMPTS;
    }

    public CsQuizAttempt addAttempt(String answerText, Integer selectedChoiceIndex, Boolean correct, CsQuizFeedback feedback) {
        CsQuizAttempt attempt = new CsQuizAttempt(this, answerText, selectedChoiceIndex, correct, feedback);
        this.attempts.add(attempt);
        return attempt;
    }

    public boolean isMultipleChoice() {
        return this.type == CsQuizQuestionType.MULTIPLE_CHOICE;
    }

    public boolean isShortAnswer() {
        return this.type == CsQuizQuestionType.SHORT_ANSWER;
    }

    public boolean isCorrectChoiceIndex(Integer selected) {
        if (!isMultipleChoice()) throw new IllegalStateException("객관식 문제가 아닙니다.");
        if (selected == null) return false;
        return selected.equals(this.correctChoiceIndex);
    }

    public Long getId() {
        return id;
    }

    public CsQuizSession getSession() {
        return session;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public CsQuizTopic getTopic() {
        return topic;
    }

    public CsQuizDifficulty getDifficulty() {
        return difficulty;
    }

    public CsQuizQuestionType getType() {
        return type;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    public int getCorrectChoiceIndexForGrading() {
        if (!isMultipleChoice()) throw new IllegalStateException("객관식 문제가 아닙니다.");
        if (correctChoiceIndex == null) throw new IllegalStateException("정답 인덱스가 설정되지 않았습니다.");
        return correctChoiceIndex;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public List<String> getRubricKeywords() {
        return Collections.unmodifiableList(rubricKeywords);
    }

    public List<CsQuizAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
