package com.arenax.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.List;
public interface QuestionSetRepository extends JpaRepository<QuestionSet, String> {
    @EntityGraph(attributePaths = "questions") Optional<QuestionSet> findWithQuestionsById(String id);
    @EntityGraph(attributePaths = "questions") List<QuestionSet> findAllByOrderByCreatedAtDesc();
}
