package com.arenax.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "player_question_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_answer_question", columnNames = {"result_id", "question_index"})
})
public class PlayerQuestionAnswer {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "result_id", nullable = false) private PlayerMatchResult result;
    @Column(name = "question_index", nullable = false) private int questionIndex;
    @Column(name = "question_id", nullable = false) private String questionId;
    @Column(name = "question_prompt", nullable = false, length = 4000) private String questionPrompt;
    private String topic;
    @Column(name = "selected_option_id") private String selectedOptionId;
    @Column(name = "is_correct", nullable = false) private boolean correct;
    @Column(name = "points_earned", nullable = false) private int pointsEarned;
    @Column(name = "response_ms") private Long responseMs;
    protected PlayerQuestionAnswer() {}
    public PlayerQuestionAnswer(PlayerMatchResult result, int questionIndex, String questionId, String questionPrompt,
                                String topic, String selectedOptionId, boolean correct, int pointsEarned, Long responseMs) {
        this.result=result; this.questionIndex=questionIndex; this.questionId=questionId; this.questionPrompt=questionPrompt;
        this.topic=topic; this.selectedOptionId=selectedOptionId; this.correct=correct; this.pointsEarned=pointsEarned; this.responseMs=responseMs;
    }
    public int getQuestionIndex() { return questionIndex; }
    public String getQuestionId() { return questionId; }
    public String getQuestionPrompt() { return questionPrompt; }
    public String getTopic() { return topic; }
    public String getSelectedOptionId() { return selectedOptionId; }
    public boolean isCorrect() { return correct; }
    public int getPointsEarned() { return pointsEarned; }
    public Long getResponseMs() { return responseMs; }
}
