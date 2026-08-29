package com.flowora.erp.identity;

import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SecurityAuditService {
    private final JdbcTemplate jdbcTemplate;

    public SecurityAuditService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public void record(
            String userId,
            String organizationId,
            String eventCode,
            String outcome,
            HttpServletRequest request,
            String detailsJson
    ) {
        if (jdbcTemplate == null) return;
        jdbcTemplate.update("""
                INSERT INTO flowora_security_event (
                    id, user_id, organization_id, event_code, outcome, request_id,
                    ip_address, user_agent, details_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), userId, organizationId, eventCode, outcome,
                RequestIdFilter.get(request), clientIp(request), trim(request.getHeader("User-Agent"), 512), detailsJson
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return trim(value, 64);
    }

    private String trim(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
