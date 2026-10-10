package com.arenax.domain;

import com.arenax.domain.QuestionSetService.QuestionInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/question-sets")
public class QuestionSetController {
    private final QuestionSetService service;
    public QuestionSetController(QuestionSetService service) { this.service=service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedQuestionSet create(@Valid @RequestBody CreateQuestionSet request) {
        for (QuestionInput q : request.questions()) {
            if (q == null) throw new IllegalArgumentException("Question entries cannot be null");
            if (q.prompt() == null || q.prompt().isBlank()) throw new IllegalArgumentException("Question prompt is required");
            if (q.options() == null || q.options().size() < 2) throw new IllegalArgumentException("Each question needs at least two options");
            if (q.options().stream().anyMatch(o -> o == null || o.id() == null || o.id().isBlank() || o.text() == null || o.text().isBlank()))
                throw new IllegalArgumentException("Every option needs an id and text");
            if (q.options().stream().noneMatch(o -> java.util.Objects.equals(o.id(), q.correctOptionId()))) throw new IllegalArgumentException("Correct option must match an option id");
        }
        return new CreatedQuestionSet(service.create(request.title().trim(), request.hostId(), request.questions()));
    }
    @GetMapping("/{id}") public QuestionSetService.QuestionSetView get(@PathVariable String id) { return service.get(id); }

    public record CreateQuestionSet(@NotBlank @Size(max=200) String title, String hostId,
                                    @NotEmpty @Valid List<@Valid QuestionInput> questions) {}
    public record CreatedQuestionSet(String id) {}
}
