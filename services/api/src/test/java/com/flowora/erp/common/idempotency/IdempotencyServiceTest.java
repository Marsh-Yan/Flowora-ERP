package com.flowora.erp.common.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimsAKeyOnlyOnce() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        IdempotencyService service = new IdempotencyService(jdbcTemplate);

        assertThat(service.claim("org-a", "SALES_DELIVERY", "retry-1")).isTrue();
    }

    @Test
    void treatsTheDatabaseUniqueKeyAsAReplay() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
        IdempotencyService service = new IdempotencyService(jdbcTemplate);

        assertThat(service.claim("org-a", "SALES_DELIVERY", "retry-1")).isFalse();
    }
}
