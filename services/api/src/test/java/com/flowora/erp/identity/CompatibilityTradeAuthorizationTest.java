package com.flowora.erp.identity;

import com.flowora.erp.procurement.*;
import com.flowora.erp.sales.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise real method-security proxies rather than direct annotation inspection. */
@SpringJUnitConfig(CompatibilityTradeAuthorizationTest.Config.class)
class CompatibilityTradeAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean FloworaAuthorization floworaAuthorization() { return new FloworaAuthorization(); }
        @Bean ProcurementService procurementService() { return mock(ProcurementService.class); }
        @Bean SalesService salesService() { return mock(SalesService.class); }
        @Bean ProcurementController procurement(ProcurementService s, FloworaAuthorization a) { return new ProcurementController(s,a); }
        @Bean SalesController sales(SalesService s, FloworaAuthorization a) { return new SalesController(s,a); }
    }
    @Autowired FloworaAuthorization auth;
    @Autowired ProcurementController procurement;
    @Autowired SalesController sales;
    @Autowired ProcurementService purchases;
    @Autowired SalesService selling;
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final PageRequest page = PageRequest.of(0,20);
    @BeforeEach void reset() { auth.setAuthenticator(null); clearInvocations(purchases,selling); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void legacyRoleAuthoritiesCannotSubstituteActualMutationPermissions() {
        var a=login(DataScope.ALL,List.of("ADMIN","BUSINESS","FINANCE","WAREHOUSE","MANAGEMENT"),List.of("sales:view","procurement:view"));
        assertThatThrownBy(()->procurement.createOrder(null,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->procurement.cancelOrder("id",a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.createOrder(null,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.deliver(null,a,request,"key")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.pay(null,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.approveQuote("id",a,request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(purchases,selling);
    }
    @Test void customCapabilitiesPermitDirectApprovedPurchaseAndConfirmedSale() {
        var a=login(DataScope.ALL,List.of("CUSTOM"),List.of("procurement:create","procurement:submit","sales:create","sales:submit"));
        procurement.createOrder(null,a,request); sales.createOrder(null,a,request);
        verify(purchases).createOrder((FloworaPrincipal)a.getPrincipal(),null);
        verify(selling).createOrder((FloworaPrincipal)a.getPrincipal(),null);
        var createOnly=login(DataScope.ALL,List.of("BUSINESS"),List.of("sales:create"));
        assertThatThrownBy(()->sales.createOrder(null,createOnly,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->procurement.createOrder(null,createOnly,request)).isInstanceOf(AccessDeniedException.class);
        verify(purchases,times(1)).createOrder(any(),any());
        verify(selling,times(1)).createOrder(any(),any());
    }
    @Test void implicitWorkflowSubmissionRequiresEveryCapability() {
        var bits=List.of("sales:view","sales:create","sales:submit","workflow:view","workflow:submit","procurement:view","procurement:create","procurement:submit");
        for(String missing:List.of("workflow:view","workflow:submit")) {
            var a=login(DataScope.ALL,List.of("ADMIN"),bits.stream().filter(p->!p.equals(missing)).toList());
            assertThatThrownBy(()->procurement.createRequest(null,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.createQuote(null,a,request)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(purchases,selling);
        var a=login(DataScope.ALL,List.of("CUSTOM"),bits);
        procurement.createRequest(null,a,request); sales.createQuote(null,a,request);
        verify(purchases).createRequest((FloworaPrincipal)a.getPrincipal(),null);
        verify(selling).createQuote(eq((FloworaPrincipal)a.getPrincipal()),isNull(),anyString());
    }
    @Test void quoteDecisionsRequireModuleReadAndWorkflowApprovalTogether() {
        for(var bits:List.of(List.of("sales:view"),List.of("workflow:approve"))) {
            var a=login(DataScope.ALL,List.of("ADMIN"),bits);
            assertThatThrownBy(()->sales.approveQuote("id",a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.rejectQuote("id",a,request)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(selling);
        var a=login(DataScope.ALL,List.of("CUSTOM"),List.of("sales:view","workflow:approve"));
        sales.approveQuote("id",a,request);
        verify(selling).approveQuote(eq((FloworaPrincipal)a.getPrincipal()),eq("id"),anyString());
    }
    @Test void unsupportedSharedScopesAreDeniedWhileScopedOrdersRemainReadable() {
        for(var scope:List.of(DataScope.SELF,DataScope.DEPARTMENT,DataScope.ASSIGNED)) {
            var a=login(scope,List.of("ADMIN"),List.of("sales:view","procurement:view","sales:create","sales:submit","finance:post","inventory:post","workflow:approve"));
            assertThatThrownBy(()->procurement.requests("",page,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.quotes("",page,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.receivables("",page,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.payments("id",page,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.deliveries("",page,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.createOrder(null,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.approveQuote("id",a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.pay(null,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->sales.deliver(null,a,request,"key")).isInstanceOf(AccessDeniedException.class);
            procurement.orders("",page,a,request); sales.orders("",page,a,request);
        }
        verify(purchases,times(3)).orders(any(),eq(""),eq(page));
        verify(selling,times(3)).orders(any(),eq(""),eq(page));
        verifyNoMoreInteractions(purchases,selling);
    }
    @Test void financeAndStockCustomPostingCapabilitiesDoNotNeedSalesRoleNames() {
        var a=login(DataScope.ALL,List.of("CUSTOM"),List.of("finance:post","inventory:post"));
        sales.deliver(null,a,request,"key"); sales.pay(null,a,request);
        verify(selling).deliver((FloworaPrincipal)a.getPrincipal(),null,"key");
        verify(selling).pay((FloworaPrincipal)a.getPrincipal(),null);
    }
    @Test void servicesReceiveFreshScopeAndCurrentRevocationsRejectCachedRoles() {
        var a=login(DataScope.ALL,List.of("BUSINESS"),List.of("procurement:view","sales:view","procurement:create","sales:create","sales:submit"));
        var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        var fresh=actor(DataScope.SELF,List.of("CUSTOM"),List.of("procurement:view","sales:view"));
        when(resolver.principalForOrganization("user","org")).thenReturn(fresh);
        procurement.orders("",page,a,request); sales.orders("",page,a,request);
        verify(purchases).orders(fresh,"",page); verify(selling).orders(fresh,"",page);
        assertThatThrownBy(()->procurement.createOrder(null,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.createOrder(null,a,request)).isInstanceOf(AccessDeniedException.class);
        verifyNoMoreInteractions(purchases,selling);
    }
    @Test void invalidLiveMembershipCannotReachServices() {
        var a=login(DataScope.ALL,List.of("ADMIN"),List.of("procurement:view","sales:view"));
        var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        when(resolver.principalForOrganization("user","org")).thenThrow(new AccessDeniedException("Inactive membership"));
        assertThatThrownBy(()->procurement.orders("",page,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->sales.quotes("",page,a,request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(purchases,selling);
    }
    Authentication login(DataScope scope,List<String> roles,List<String> bits) {
        var p=actor(scope,roles,bits); var a=UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(a); return a;
    }
    FloworaPrincipal actor(DataScope scope,List<String> roles,List<String> bits) {
        return new FloworaPrincipal("user","user@audit.invalid","Tester","org","Org","membership",null,scope,roles,bits,false);
    }
}
