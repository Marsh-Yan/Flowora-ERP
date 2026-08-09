package com.flowora.erp.common.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class IdempotencyService {
    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claim(String organizationId, String operation, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isBlank()) return true;
        try {
            jdbcTemplate.update(
                    "INSERT INTO flowora_idempotency_record (id, organization_id, operation, operation_key) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID().toString(), organizationId, operation, key
            );
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
