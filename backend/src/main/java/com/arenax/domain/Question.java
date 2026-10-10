package com.arenax.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "set_id") private QuestionSet questionSet;
    @Column(nullable = false, length = 4000) private String prompt;
    @Column(name = "options_json", nullable = false, columnDefinition = "text") private String optionsJson;
    @Column(name = "correct_option_id", nullable = false) private String correctOptionId;
    private String topic;
    private String difficulty;
    @Column(name = "image_url") private String imageUrl;
    @Column(name = "table_data_json", columnDefinition = "text") private String tableDataJson;
    protected Question() {}
    public Question(QuestionSet set, String prompt, String optionsJson, String correctOptionId,
                    String topic, String difficulty, String imageUrl, String tableDataJson) {
        this.questionSet=set; this.prompt=prompt; this.optionsJson=optionsJson; this.correctOptionId=correctOptionId;
        this.topic=topic; this.difficulty=difficulty; this.imageUrl=imageUrl; this.tableDataJson=tableDataJson;
    }
    public String getId() { return id; } public QuestionSet getQuestionSet() { return questionSet; }
    public String getPrompt() { return prompt; } public String getOptionsJson() { return optionsJson; }
    public String getCorrectOptionId() { return correctOptionId; } public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; } public String getImageUrl() { return imageUrl; }
    public String getTableDataJson() { return tableDataJson; }
    public Question copyFor(QuestionSet set) {
        return new Question(set, prompt, optionsJson, correctOptionId, topic, difficulty, imageUrl, tableDataJson);
    }
}
