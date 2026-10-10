package com.arenax.domain;

import com.arenax.game.AdminAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/question-sets")
public class AdminQuestionSetController {
    public record SaveSet(@NotBlank @Size(max = 200) String title,
                          @NotEmpty @Valid List<@Valid QuestionSetService.QuestionInput> questions) {}
    public record ArchiveRequest(boolean archived) {}
    public record Created(String id) {}

    private final AdminAuthService auth;
    private final QuestionSetService sets;

    public AdminQuestionSetController(AdminAuthService auth, QuestionSetService sets) {
        this.auth = auth;
        this.sets = sets;
    }

    @GetMapping
    public List<QuestionSetService.QuestionSetView> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization);
        return sets.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @Valid @RequestBody SaveSet request) {
        String owner = auth.requireAdmin(authorization);
        validateQuestions(request.questions());
        return new Created(sets.create(request.title().trim(), owner, request.questions()));
    }

    @PutMapping("/{id}")
    public QuestionSetService.QuestionSetView update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String id, @Valid @RequestBody SaveSet request) {
        auth.requireAdmin(authorization);
        validateQuestions(request.questions());
        return sets.update(id, request.title().trim(), request.questions());
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public Created duplicate(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String id) {
        String owner = auth.requireAdmin(authorization);
        return new Created(sets.duplicate(id, owner));
    }

    @PatchMapping("/{id}/archive")
    public void archive(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable String id, @RequestBody ArchiveRequest request) {
        auth.requireAdmin(authorization);
        sets.archive(id, request.archived());
    }

    private void validateQuestions(List<QuestionSetService.QuestionInput> questions) {
        for (var q : questions) {
            if (q == null || q.prompt() == null || q.prompt().isBlank()) throw new IllegalArgumentException("Question prompt is required");
            if (q.prompt().length() > 4000) throw new IllegalArgumentException("Question prompts are limited to 4000 characters");
            if (q.options() == null || q.options().size() < 2 || q.options().size() > 6) throw new IllegalArgumentException("Each question needs 2–6 options");
            if (q.options().stream().anyMatch(o -> o == null || o.id() == null || o.id().isBlank() || o.text() == null || o.text().isBlank()))
                throw new IllegalArgumentException("Every option needs an ID and text");
            if (q.options().stream().map(o -> o.id().trim()).distinct().count() != q.options().size())
                throw new IllegalArgumentException("Option IDs must be unique");
            if (q.options().stream().map(o -> o.text().trim().toLowerCase()).distinct().count() != q.options().size())
                throw new IllegalArgumentException("Duplicate options are not allowed");
            if (q.options().stream().noneMatch(o -> o.id().equals(q.correctOptionId())))
                throw new IllegalArgumentException("Correct option must match an option ID");
        }
    }
}
