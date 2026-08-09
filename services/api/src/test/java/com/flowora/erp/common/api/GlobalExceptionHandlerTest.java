package com.flowora.erp.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void mapsIllegalStateToAConflictWithTheRequestId() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "req-state-1");

        var response = handler.handleStateConflict(new IllegalStateException("already posted"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("STATE_CONFLICT");
        assertThat(response.getBody().requestId()).isEqualTo("req-state-1");
    }

    @Test
    void mapsMethodSecurityDenialToForbidden() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "req-forbidden-1");

        var response = handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTH_FORBIDDEN");
        assertThat(response.getBody().requestId()).isEqualTo("req-forbidden-1");
    }
}
