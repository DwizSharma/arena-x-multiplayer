package com.arenax.game;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RoomCoordinationAspect {
    private final DistributedRoomCoordinator coordinator;
    private final RoomEngine engine;
    public RoomCoordinationAspect(DistributedRoomCoordinator coordinator,@Lazy RoomEngine engine) {
        this.coordinator=coordinator;this.engine=engine;
    }
    @Around("@annotation(operation)")
    public Object coordinate(ProceedingJoinPoint call,SharedRoomOperation operation) throws Throwable {
        String roomCode=operation.global()?"room-create":String.valueOf(call.getArgs()[operation.codeArgument()]).toUpperCase(java.util.Locale.ROOT);
        try(var lock=coordinator.acquire(roomCode)) {
            if(!operation.global()) engine.refreshFromSharedSnapshot(roomCode);
            return call.proceed();
        }
    }
}
