package com.flowora.erp.identity;

import com.flowora.erp.common.api.GlobalExceptionHandler;
import com.flowora.erp.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.autoconfigure.ImportAutoConfiguration(org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration.class)
@WebMvcTest(SessionV2Controller.class)
@Import({SecurityConfig.class, DemoUserStore.class, SecurityAuditService.class, GlobalExceptionHandler.class})
class SessionV2ControllerTest {
    private static final String CSRF_TOKEN = "m1-session-test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionGovernanceService sessionGovernance;

    @Test
    void logsInThroughV2AndReturnsEffectivePermissions() throws Exception {
        mockMvc.perform(post("/api/v2/session/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"operator@demo.flowora\",\"password\":\"Demo123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationId").value("org-demo"))
                .andExpect(jsonPath("$.data.permissions[0]").isString())
                .andExpect(jsonPath("$.data.dataScope").value("ALL"));
    }

    @Test
    void usesV2StableAuthenticationErrorCode() throws Exception {
        mockMvc.perform(post("/api/v2/session/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"unknown@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void protectsSessionLookup() throws Exception {
        mockMvc.perform(get("/api/v2/session/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void allowsIfMatchPreflightOnlyFromAnAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v2/workflow/tasks/example/act")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "If-Match,X-XSRF-TOKEN"))
                .andExpect(status().isOk());
        mockMvc.perform(options("/api/v2/workflow/tasks/example/act")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "If-Match"))
                .andExpect(status().isForbidden());
    }
}
