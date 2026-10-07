package com.flowora.erp.identity;

import com.flowora.erp.common.api.GlobalExceptionHandler;
import com.flowora.erp.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import java.util.List;

@org.springframework.boot.autoconfigure.ImportAutoConfiguration(org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration.class)
@WebMvcTest({AuthController.class, SessionV2Controller.class})
@Import({SecurityConfig.class, DemoUserStore.class, GlobalExceptionHandler.class})
class AuthControllerTest {
    private static final String CSRF_TOKEN = "phase-01-test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityAuditService auditService;

    @MockitoBean
    private SessionGovernanceService sessionGovernance;

    @MockitoBean
    private DatabaseMfaService mfaService;

    @Test
    void rejectsUnauthenticatedSessionLookup() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void logsInWithDemoAccountAndReturnsOrganizationContext() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"operator@demo.flowora\",\"password\":\"Demo123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operator@demo.flowora"))
                .andExpect(jsonPath("$.data.organizationId").value("org-demo"))
                .andExpect(jsonPath("$.data.roles[0]").value("BUSINESS"))
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test
    void rejectsInvalidCredentialsWithStableErrorCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"operator@demo.flowora\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.messageKey").value("errors.authInvalidCredentials"));
    }

    @Test
    void logsOutAndInvalidatesTheSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"operator@demo.flowora\",\"password\":\"Demo123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blocksBusinessRequestsUntilTemporaryPasswordIsChanged() throws Exception {
        FloworaPrincipal principal = new FloworaPrincipal("user-a", "alice", "Alice", "org-a", "Org A",
                "membership-a", null, DataScope.ALL, List.of("BUSINESS"), List.of("sales:view"), true);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));

        mockMvc.perform(get("/api/v2/compat/sales/orders").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v2/session/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void v1LoginCannotBypassAnEnabledMfaFactor() throws Exception {
        when(mfaService.required("user-demo-operator")).thenReturn(true);
        String credentials = "{\"username\":\"operator@demo.flowora\",\"password\":\"Demo123!\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json").content(credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", CSRF_TOKEN))
                        .header("X-XSRF-TOKEN", CSRF_TOKEN)
                        .contentType("application/json")
                        .content("{\"username\":\"operator@demo.flowora\",\"password\":\"Demo123!\",\"mfaCode\":\"123456\"}"))
                .andExpect(status().isOk());
        verify(mfaService).verifyLogin("user-demo-operator", "123456");
    }
}
