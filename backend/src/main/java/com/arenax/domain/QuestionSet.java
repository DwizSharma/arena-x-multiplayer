package com.arenax.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "question_sets")
public class QuestionSet {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private String title;
    @Column(name = "host_id") private String hostId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private boolean archived;
    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "question_order") private List<Question> questions = new ArrayList<>();
    protected QuestionSet() {}
    public QuestionSet(String title, String hostId) { this.title = title; this.hostId = hostId; }
    public String getId() { return id; } public String getTitle() { return title; }
    public String getHostId() { return hostId; } public Instant getCreatedAt() { return createdAt; }
    public List<Question> getQuestions() { return questions; }
    public boolean isArchived() { return archived; }
    public void update(String title, List<Question> replacement) {
        this.title = title;
        questions.clear();
        replacement.forEach(question -> questions.add(question));
    }
    public void archive() { this.archived = true; }
    public void restore() { this.archived = false; }
}
