package com.arenax.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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
    public void putCritical(String key,Object value) {
        try {
            redis.opsForValue().set("arena:"+key,mapper.writeValueAsString(value),Duration.ofHours(24));
            if(key.startsWith("room-state:")) {
                redis.opsForSet().add("arena:room-codes",key.substring("room-state:".length()));
                redis.expire("arena:room-codes",Duration.ofHours(24));
            }
        }
        catch(Exception e) { throw new IllegalStateException("Could not persist shared room/session state",e); }
    }
    public <T> T getCritical(String key,Class<T> type) {
        try { String json=redis.opsForValue().get("arena:"+key); return json==null?null:mapper.readValue(json,type); }
        catch(Exception e) { throw new IllegalStateException("Could not read shared room/session state",e); }
    }
    public void delete(String key) { redis.delete("arena:"+key); }
    public List<RoomStateSnapshot> getRoomSnapshots() {
        List<RoomStateSnapshot> snapshots=new ArrayList<>();
        java.util.Set<String> codes=redis.opsForSet().members("arena:room-codes");
        if(codes==null)return snapshots;
        for(String code:codes) {
            RoomStateSnapshot snapshot=getCritical("room-state:"+code,RoomStateSnapshot.class);
            if(snapshot==null)redis.opsForSet().remove("arena:room-codes",code); else snapshots.add(snapshot);
        }
        return snapshots;
    }
}
