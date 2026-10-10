package com.arenax.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;

@Component
public class RoomSnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(RoomSnapshotCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    public RoomSnapshotCache(StringRedisTemplate redis, ObjectMapper mapper) { this.redis=redis; this.mapper=mapper; }
    public void put(String key, Object value) {
        try {
            redis.opsForValue().set("arena:" + key, mapper.writeValueAsString(value), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Room snapshot cache update failed for {}: {}", key, e.getMessage());
        }
    }
}
