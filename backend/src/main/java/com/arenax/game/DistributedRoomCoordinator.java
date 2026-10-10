package com.arenax.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class DistributedRoomCoordinator {
    private static final Logger log=LoggerFactory.getLogger(DistributedRoomCoordinator.class);
    private static final Duration LEASE=Duration.ofSeconds(45);
    private static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",Long.class);
    private static final DefaultRedisScript<Long> RENEW=new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",Long.class);
    private static final ScheduledExecutorService renewer=Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"arena-room-lock-renewer");t.setDaemon(true);return t;});
    private final StringRedisTemplate redis;
    private final ThreadLocal<java.util.Map<String,LockHandle>> held=ThreadLocal.withInitial(java.util.HashMap::new);
    public DistributedRoomCoordinator(StringRedisTemplate redis) { this.redis=redis; }

    public LockHandle acquire(String key) {
        var current=held.get().get(key);
        if(current!=null) { current.depth++; return current; }
        String redisKey="arena:lock:"+key, token=UUID.randomUUID().toString();
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
        while(System.nanoTime()<deadline) {
            Boolean acquired=redis.opsForValue().setIfAbsent(redisKey,token,LEASE);
            if(Boolean.TRUE.equals(acquired)) {
                LockHandle handle=new LockHandle(key,redisKey,token); held.get().put(key,handle);
                handle.renewal=renewer.scheduleAtFixedRate(()->{
                    try {
                        Long renewed=redis.execute(RENEW,List.of(redisKey),token,String.valueOf(LEASE.toMillis()));
                        if(!Long.valueOf(1).equals(renewed)) log.warn("Lost distributed room lock for {}",key);
                    } catch(RuntimeException error) {
                        log.warn("Could not renew distributed room lock for {}: {}",key,error.getMessage());
                    }
                },10,10,TimeUnit.SECONDS);
                return handle;
            }
            try { Thread.sleep(25); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for room ownership",e); }
        }
        throw new IllegalStateException("Room is busy on another backend. Retry the request.");
    }

    public final class LockHandle implements AutoCloseable {
        private final String key,redisKey,token; private int depth=1; private boolean closed; private ScheduledFuture<?> renewal;
        private LockHandle(String key,String redisKey,String token) { this.key=key;this.redisKey=redisKey;this.token=token; }
        @Override public void close() {
            if(closed)return;
            if(--depth>0)return;
            var locks=held.get();
            locks.remove(key);
            if(locks.isEmpty())held.remove();
            closed=true;
            if(renewal!=null)renewal.cancel(false);
            redis.execute(RELEASE,List.of(redisKey),token);
        }
    }
}
