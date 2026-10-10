package com.arenax.game;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record LoginRequest(@NotBlank @Size(max = 120) String username, @NotBlank @Size(max = 256) String password) {}
    public record CreateRoomRequest(@NotBlank String mode, @Size(max = 200) String questionSetId) {}
    public record CreateMomentRequest(@NotBlank String kind) {}
    public record Message(String message) {}

    private final AdminAuthService auth;
    private final RoomEngine rooms;

    public AdminController(AdminAuthService auth, RoomEngine rooms) { this.auth = auth; this.rooms = rooms; }

    @PostMapping("/login")
    public AdminAuthService.Login login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username().trim(), request.password());
    }

    @PostMapping("/logout")
    public Message logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
        return new Message("Signed out");
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public GameTypes.AdminRoomView createRoom(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @Valid @RequestBody CreateRoomRequest request) {
        auth.requireAdmin(authorization);
        return rooms.createPrivateRoom(request.mode(), request.questionSetId());
    }

    @GetMapping("/rooms/{code}")
    public GameTypes.AdminRoomView room(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable String code) {
        auth.requireAdmin(authorization);
        return rooms.adminRoom(code);
    }

    @PostMapping("/rooms/{code}/start")
    public GameTypes.AdminRoomView startRoom(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @PathVariable String code) {
        auth.requireAdmin(authorization);
        return rooms.startPrivateRoom(code);
    }

    @PostMapping("/rooms/{code}/moments")
    public GameTypes.RoomMoment moment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable String code, @Valid @RequestBody CreateMomentRequest request) {
        auth.requireAdmin(authorization);
        return rooms.launchMoment(code, request.kind());
    }
}
