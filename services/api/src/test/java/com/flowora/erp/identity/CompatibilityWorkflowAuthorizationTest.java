package com.flowora.erp.identity;

import com.flowora.erp.workflow.*;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.v2.WorkflowResourceAccessPolicy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(CompatibilityWorkflowAuthorizationTest.Config.class)
class CompatibilityWorkflowAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean FloworaAuthorization floworaAuthorization() { return new FloworaAuthorization(); }
        @Bean WorkflowService workflowService() { return mock(WorkflowService.class); }
        @Bean WorkflowResourceAccessPolicy resourceAccess() { return mock(WorkflowResourceAccessPolicy.class); }
        @Bean WorkflowController workflow(WorkflowService s, FloworaAuthorization a, ObjectProvider<WorkflowResourceAccessPolicy> p) {
            return new WorkflowController(s, a, p);
        }
    }
    @Autowired FloworaAuthorization authorization;
    @Autowired WorkflowService service;
    @Autowired WorkflowResourceAccessPolicy resources;
    @Autowired WorkflowController controller;
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final TaskRequest task = new TaskRequest(WorkflowResourceType.PURCHASE_ORDER, "po", "Synthetic", null, BigDecimal.TEN, null, null);
    @BeforeEach void resetMocks() { authorization.setAuthenticator(null); reset(service, resources); }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void fixedRoleNamesCannotCreateWithoutSubmitPermission() {
        var a = login(actor(List.of("ADMIN", "BUSINESS", "FINANCE", "WAREHOUSE", "PROJECT"), List.of("workflow:view")));
        assertThatThrownBy(() -> controller.createTask(task, a, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service, resources);
    }
    @Test void customSubmitterReceivesResourceCheckBeforeCreation() {
        var actor = actor(List.of("CUSTOM"), List.of("workflow:submit"));
        var a = login(actor);
        controller.createTask(task, a, request);
        var order = inOrder(resources, service);
        order.verify(resources).require(actor, "PURCHASE_ORDER", "po", "read");
        order.verify(service).createTask(eq(actor), eq(task), anyString());
    }
    @Test void failedResourceAccessNeverCreatesTask() {
        var actor = actor(List.of("CUSTOM"), List.of("workflow:submit"));
        var a = login(actor);
        when(resources.require(actor, "PURCHASE_ORDER", "po", "read")).thenThrow(new AccessDeniedException("Scope"));
        assertThatThrownBy(() -> controller.createTask(task, a, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test void liveIdentityRefreshPreventsStaleRoleAndSubmitPermission() {
        var a = login(actor(List.of("ADMIN"), List.of("workflow:submit")));
        var resolver = mock(DatabaseIdentityAuthenticator.class);
        authorization.setAuthenticator(resolver);
        when(resolver.principalForOrganization("user", "org")).thenReturn(actor(List.of("ADMIN"), List.of()));
        assertThatThrownBy(() -> controller.createTask(task, a, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service, resources);
    }
    @Test void markingNotificationsReadRequiresWorkflowView() {
        var a = login(actor(List.of("ADMIN"), List.of()));
        assertThatThrownBy(() -> controller.markNotificationRead("id", a, request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    Authentication login(FloworaPrincipal actor) {
        var a = UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(a); return a;
    }
    FloworaPrincipal actor(List<String> roles, List<String> permissions) {
        return new FloworaPrincipal("user", "user@audit.invalid", "Synthetic", "org", "Org", "membership", null,
                DataScope.ALL, roles, permissions, false);
    }
}
