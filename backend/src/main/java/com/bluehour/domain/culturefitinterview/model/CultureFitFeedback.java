package com.bluehour.domain.culturefitinterview.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Embeddable
public class CultureFitFeedback {

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "culture_fit_feedback_strengths", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "strength", length = 2000)
    @OrderColumn(name = "idx")
    private List<String> strengths = new ArrayList<>();

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "culture_fit_feedback_improvements", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "improvement", length = 2000)
    @OrderColumn(name = "idx")
    private List<String> improvements = new ArrayList<>();

    @Column(name = "suggested_answer", columnDefinition = "text")
    private String suggestedAnswer;

    @BatchSize(size = 50)
    @ElementCollection
    @CollectionTable(name = "culture_fit_feedback_followups", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "followup", length = 2000)
    @OrderColumn(name = "idx")
    private List<String> followups = new ArrayList<>();

    @Column(name = "alignment_note", columnDefinition = "text")
    private String alignmentNote;

    protected CultureFitFeedback() {
    }

    public CultureFitFeedback(
            List<String> strengths,
            List<String> improvements,
            String suggestedAnswer,
            List<String> followups,
            String alignmentNote
    ) {
        if (strengths != null) this.strengths = new ArrayList<>(strengths);
        if (improvements != null) this.improvements = new ArrayList<>(improvements);
        this.suggestedAnswer = suggestedAnswer;
        if (followups != null) this.followups = new ArrayList<>(followups);
        this.alignmentNote = alignmentNote;
    }

    public List<String> getStrengths() {
        return Collections.unmodifiableList(strengths);
    }

    public List<String> getImprovements() {
        return Collections.unmodifiableList(improvements);
    }

    public String getSuggestedAnswer() {
        return suggestedAnswer;
    }

    public List<String> getFollowups() {
        return Collections.unmodifiableList(followups);
    }

    public String getAlignmentNote() {
        return alignmentNote;
    }
}
