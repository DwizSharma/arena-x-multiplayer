package com.arenax.game;

import com.arenax.game.GameTypes.Answer;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class RoomController {
    public record RoomRequest(String roomCode) {}
    public record StartRequest(String questionSetId, String mode) {}
    private final RoomEngine engine;
    private final SimpMessagingTemplate messaging;
    public RoomController(RoomEngine engine, SimpMessagingTemplate messaging) { this.engine=engine; this.messaging=messaging; }
    @MessageMapping("/room/{code}/join")
    public void join(@DestinationVariable String code, SimpMessageHeaderAccessor headers) {
        SessionRegistry.Identity identity = identity(headers);
        engine.join(code, identity);
        messaging.convertAndSendToUser(identity.playerId(), "/queue/joined", engine.snapshot(code, identity.playerId()));
    }
    @MessageMapping("/room/{code}/start")
    public void start(@DestinationVariable String code, @Payload(required = false) StartRequest request, SimpMessageHeaderAccessor headers) {
        identity(headers); engine.start(code, request == null ? null : request.questionSetId(), request == null ? null : request.mode());
    }
    @MessageMapping("/room/{code}/answer")
    public void answer(@DestinationVariable String code, @Payload Answer answer, SimpMessageHeaderAccessor headers) {
        engine.submit(code, identity(headers).playerId(), answer);
    }
    @MessageMapping("/room/{code}/reconnect")
    public void reconnect(@DestinationVariable String code, SimpMessageHeaderAccessor headers) {
        SessionRegistry.Identity identity=identity(headers);
        messaging.convertAndSendToUser(identity.playerId(), "/queue/reconnect-state", engine.snapshot(code, identity.playerId()));
    }
    @MessageMapping("/room/{code}/watch")
    public void watch(@DestinationVariable String code, SimpMessageHeaderAccessor headers) {
        identity(headers); engine.watch(code);
    }
    private SessionRegistry.Identity identity(SimpMessageHeaderAccessor headers) {
        Object identity=headers.getSessionAttributes()==null?null:headers.getSessionAttributes().get("identity");
        if (!(identity instanceof SessionRegistry.Identity sessionIdentity)) throw new IllegalArgumentException("Unauthenticated session");
        return sessionIdentity;
    }
}
