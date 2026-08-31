package com.flowora.erp.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyApiReadOnlyFilterTest {
    private final LegacyApiReadOnlyFilter filter = new LegacyApiReadOnlyFilter(new ObjectMapper(), true);

    @Test
    void rejectsLegacyBusinessWritesWithUpgradeRequired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sales/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, noOpChain());

        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getHeader("Upgrade")).isEqualTo("Flowora-API/2");
        assertThat(response.getContentAsString()).contains("API_VERSION_READ_ONLY", "/api/v2/compat/sales/orders");
    }

    @Test
    void allowsLegacyReadsAndAuthenticationWrites() throws Exception {
        AtomicBoolean readReached = new AtomicBoolean();
        AtomicBoolean authReached = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/sales/orders"), new MockHttpServletResponse(),
                (request, response) -> readReached.set(true));
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"), new MockHttpServletResponse(),
                (request, response) -> authReached.set(true));
        assertThat(readReached).isTrue();
        assertThat(authReached).isTrue();
    }

    private FilterChain noOpChain() { return (request, response) -> { }; }
}
