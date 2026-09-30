package com.flowora.erp.identity;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionGovernanceService {
    private final FindByIndexNameSessionRepository<? extends Session> repository;
    private final StringRedisTemplate redis;

    public SessionGovernanceService(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositoryProvider,
            ObjectProvider<StringRedisTemplate> redisProvider
    ) {
        this.repository = repositoryProvider.getIfAvailable();
        this.redis = redisProvider.getIfAvailable();
    }

    public List<SessionView> sessions(String username, String currentSessionId) {
        requireRepository();
        Map<String, ? extends Session> sessions = repository.findByPrincipalName(username);
        return sessions.values().stream()
                .map(session -> new SessionView(
                        session.getId(), session.getCreationTime(), session.getLastAccessedTime(),
                        session.getMaxInactiveInterval().toSeconds(), session.isExpired(),
                        session.getId().equals(currentSessionId)
                ))
                .sorted(Comparator.comparing(SessionView::lastAccessedAt).reversed())
                .toList();
    }

    public void revoke(String username, String sessionId) {
        requireRepository();
        Session session = repository.findByPrincipalName(username).get(sessionId);
        if (session == null) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
        }
        repository.deleteById(sessionId);
        clearReservation(username, sessionId);
    }

    public void revokeAll(String username) {
        requireRepository();
        repository.findByPrincipalName(username).keySet().forEach(repository::deleteById);
        if (redis != null) redis.delete(pendingKey(username));
    }

    public void assertAvailable(String username) {
        requireRepository();
        repository.findByPrincipalName(username);
    }

    public void reserveLogin(String username, String sessionId) {
        if (repository == null && redis == null) return; // standalone profile has no shared session store
        requireRepository();
        if (redis == null) throw new PlatformApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "SESSION_STORE_UNAVAILABLE", "errors.sessionStoreUnavailable");
        String lockKey = "flowora:session-login:lock:" + username;
        String token = UUID.randomUUID().toString();
        boolean locked = false;
        for (int attempt = 0; attempt < 20 && !locked; attempt++) {
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(5)));
            if (!locked) {
                try { Thread.sleep(50); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new PlatformApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "SESSION_STORE_UNAVAILABLE", "errors.sessionStoreUnavailable");
                }
            }
        }
        if (!locked) throw new PlatformApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "SESSION_STORE_UNAVAILABLE", "errors.sessionStoreUnavailable");
        try {
            Set<String> active = repository.findByPrincipalName(username).keySet();
            String pendingKey = pendingKey(username);
            Set<String> pending = redis.opsForSet().members(pendingKey);
            if (pending != null) {
                active.forEach(id -> redis.opsForSet().remove(pendingKey, id));
                pending.removeAll(active);
            }
            if (active.size() + (pending == null ? 0 : pending.size()) >= 3) {
                throw new PlatformApiException(HttpStatus.CONFLICT, "SESSION_LIMIT_REACHED", "errors.sessionLimitReached");
            }
            redis.opsForSet().add(pendingKey, sessionId);
            redis.expire(pendingKey, Duration.ofSeconds(30));
        } finally {
            DefaultRedisScript<Long> release = new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);
            redis.execute(release, List.of(lockKey), token);
        }
    }

    public void clearReservation(String username, String sessionId) {
        if (redis != null) redis.opsForSet().remove(pendingKey(username), sessionId);
    }

    private String pendingKey(String username) {
        return "flowora:session-login:pending:" + username;
    }

    private void requireRepository() {
        if (repository == null) {
            throw new PlatformApiException(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_STORE_UNAVAILABLE", "errors.sessionStoreUnavailable");
        }
    }

    public record SessionView(
            String id, Instant createdAt, Instant lastAccessedAt, long maxInactiveSeconds,
            boolean expired, boolean current
    ) {}
}
