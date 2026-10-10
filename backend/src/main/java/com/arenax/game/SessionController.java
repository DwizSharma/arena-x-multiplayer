package com.arenax.game;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    public record CreateSession(@NotBlank String name, @NotBlank String college) {}
    private final SessionRegistry sessions;
    public SessionController(SessionRegistry sessions) { this.sessions = sessions; }
    @PostMapping public SessionRegistry.Identity create(@Valid @RequestBody CreateSession request) {
        return sessions.issue(request.name().trim(), request.college().trim());
    }
}
