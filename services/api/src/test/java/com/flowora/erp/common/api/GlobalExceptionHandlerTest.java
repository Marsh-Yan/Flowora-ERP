package com.flowora.erp.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void frameworkInputAndRouteErrorsKeepTheirStatusWithoutLeakingDetails() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new InputController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/input"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/input").param("date","bad").param("state","ALL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/input").param("date","2026-10-01").param("state","ALL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/unknown"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v2/input").contentType("application/json").content("{broken"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v2/input").contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v2/server-error"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.args").isEmpty());
    }
    @org.springframework.web.bind.annotation.RestController
    static class InputController {
        enum State { ACTIVE }
        @org.springframework.web.bind.annotation.GetMapping("/api/v2/input")
        String input(@org.springframework.web.bind.annotation.RequestParam java.time.LocalDate date,
                @org.springframework.web.bind.annotation.RequestParam State state) { return "ok"; }
        record Input(@jakarta.validation.constraints.NotBlank String name) {}
        @org.springframework.web.bind.annotation.PostMapping("/api/v2/input")
        String body(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Input input) { return "ok"; }
        @org.springframework.web.bind.annotation.GetMapping("/api/v2/server-error")
        String fail() { throw new RuntimeException("Synthetic service failure"); }
    }
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
