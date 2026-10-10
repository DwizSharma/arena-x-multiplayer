package com.arenax.domain;

import com.arenax.game.GameTypes.Option;
import com.arenax.game.GameTypes.Prompt;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class QuestionSetService {
    private final QuestionSetRepository repository;
    private final ObjectMapper mapper;
    public QuestionSetService(QuestionSetRepository repository, ObjectMapper mapper) { this.repository=repository; this.mapper=mapper; }

    @Transactional
    public String create(String title, String hostId, List<QuestionInput> questions) {
        QuestionSet set = new QuestionSet(title, hostId);
        for (QuestionInput q : questions) {
            try {
                set.getQuestions().add(new Question(set, q.prompt(), mapper.writeValueAsString(q.options()),
                        q.correctOptionId(), q.topic(), q.difficulty(), q.imageUrl(),
                        q.tableData() == null ? null : mapper.writeValueAsString(q.tableData())));
            } catch (Exception e) { throw new IllegalArgumentException("Could not encode question data", e); }
        }
        return repository.save(set).getId();
    }

    @Transactional(readOnly = true)
    public List<Prompt> loadPrompts(String id) {
        QuestionSet set = repository.findWithQuestionsById(id).orElseThrow(() -> new IllegalArgumentException("Question set not found: " + id));
        if (set.getQuestions().isEmpty()) throw new IllegalArgumentException("Question set is empty");
        return set.getQuestions().stream().map(q -> {
            try {
                List<Option> options = mapper.readValue(q.getOptionsJson(), new TypeReference<>() {});
                List<List<String>> table = q.getTableDataJson() == null ? List.of() : mapper.readValue(q.getTableDataJson(), new TypeReference<>() {});
                return new Prompt(q.getId(), q.getPrompt(), options, q.getCorrectOptionId(), q.getTopic(), 30, q.getImageUrl(), table);
            } catch (Exception e) { throw new IllegalArgumentException("Question data is invalid: " + q.getId(), e); }
        }).toList();
    }

    @Transactional(readOnly = true)
    public QuestionSetView get(String id) {
        QuestionSet set=repository.findWithQuestionsById(id).orElseThrow(() -> new IllegalArgumentException("Question set not found: " + id));
        List<QuestionView> questions=set.getQuestions().stream().map(q -> {
            try { return new QuestionView(q.getId(),q.getPrompt(),mapper.readValue(q.getOptionsJson(),new TypeReference<>(){}),q.getTopic(),q.getDifficulty(),q.getImageUrl(),q.getTableDataJson()==null?List.of():mapper.readValue(q.getTableDataJson(),new TypeReference<>(){})); }
            catch(Exception e){throw new IllegalArgumentException("Question data is invalid",e);}
        }).toList();
        return new QuestionSetView(set.getId(),set.getTitle(),set.getHostId(),set.getCreatedAt(),questions);
    }

    public record QuestionInput(String prompt, List<Option> options, String correctOptionId, String topic,
                                String difficulty, String imageUrl, List<List<String>> tableData) {}
    public record QuestionView(String id, String prompt, List<Option> options,
                               String topic, String difficulty, String imageUrl, List<List<String>> tableData) {}
    public record QuestionSetView(String id, String title, String hostId, java.time.Instant createdAt,
                                  List<QuestionView> questions) {}
}
