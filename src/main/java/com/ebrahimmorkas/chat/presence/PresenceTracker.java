package com.ebrahimmorkas.chat.presence;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cluster-wide presence that survives instance crashes.
 *
 * <p>Each instance keeps its own hash {@code presence:instance:<id>} of userId → open connection
 * count (users may have several tabs), with a short TTL that a heartbeat keeps extending. If an
 * instance dies without cleaning up, its hash simply expires and its users stop appearing online,
 * with no manual cleanup or stale "online forever" users.
 */
@Slf4j
@Component
public class PresenceTracker {

    static final String INSTANCES_KEY = "presence:instances";
    static final String INSTANCE_KEY_PREFIX = "presence:instance:";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String instanceKey;
    private final String instanceId;

    public PresenceTracker(StringRedisTemplate redis, @Value("${app.presence.ttl:30s}") Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
        this.instanceId = UUID.randomUUID().toString();
        this.instanceKey = INSTANCE_KEY_PREFIX + instanceId;
    }

    public void connected(String userId) {
        redis.opsForHash().increment(instanceKey, userId, 1);
        redis.expire(instanceKey, ttl);
        redis.opsForSet().add(INSTANCES_KEY, instanceId);
    }

    public void disconnected(String userId) {
        Long remaining = redis.opsForHash().increment(instanceKey, userId, -1);
        if (remaining <= 0) {
            redis.opsForHash().delete(instanceKey, userId);
        }
    }

    /** Which of the given users have at least one open connection on any live instance. */
    public Set<String> online(Collection<String> candidates) {
        Set<String> online = new HashSet<>();
        Set<String> instances = redis.opsForSet().members(INSTANCES_KEY);
        if (instances == null) {
            return online;
        }
        for (String instance : instances) {
            String key = INSTANCE_KEY_PREFIX + instance;
            Set<Object> users = redis.opsForHash().keys(key);
            if (users.isEmpty() && !Boolean.TRUE.equals(redis.hasKey(key))) {
                // Instance is gone (crashed or shut down): forget it
                redis.opsForSet().remove(INSTANCES_KEY, instance);
                continue;
            }
            users.forEach(user -> online.add((String) user));
        }
        online.retainAll(candidates);
        return online;
    }

    @Scheduled(fixedRateString = "${app.presence.heartbeat:10s}")
    public void heartbeat() {
        try {
            redis.expire(instanceKey, ttl);
            redis.opsForSet().add(INSTANCES_KEY, instanceId);
        } catch (RuntimeException e) {
            log.warn("Presence heartbeat failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            redis.delete(instanceKey);
            redis.opsForSet().remove(INSTANCES_KEY, instanceId);
        } catch (RuntimeException e) {
            log.debug("Presence cleanup skipped: {}", e.getMessage());
        }
    }
}
