package com.flowora.erp.identity;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class SessionGovernanceService {
    private final FindByIndexNameSessionRepository<? extends Session> repository;

    public SessionGovernanceService(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositoryProvider
    ) {
        this.repository = repositoryProvider.getIfAvailable();
    }

    public List<SessionView> sessions(String username, String currentSessionId) {
        if (repository == null) return List.of();
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
        if (repository == null) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "SESSION_STORE_UNAVAILABLE", "errors.sessionStoreUnavailable");
        }
        Session session = repository.findByPrincipalName(username).get(sessionId);
        if (session == null) {
            throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
        }
        repository.deleteById(sessionId);
    }

    public void revokeAll(String username) {
        if (repository == null) return;
        repository.findByPrincipalName(username).keySet().forEach(repository::deleteById);
    }

    public record SessionView(
            String id, Instant createdAt, Instant lastAccessedAt, long maxInactiveSeconds,
            boolean expired, boolean current
    ) {}
}
