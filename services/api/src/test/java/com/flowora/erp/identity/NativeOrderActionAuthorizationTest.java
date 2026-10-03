package com.flowora.erp.identity;

import com.flowora.erp.trade.v2.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
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

/** Real method-security proxies and ordered scope checks before transactional actions. */
@SpringJUnitConfig(NativeOrderActionAuthorizationTest.Config.class)
class NativeOrderActionAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean FloworaAuthorization floworaAuthorization() { return new FloworaAuthorization(); }
        @Bean TradeDocumentService documents() { return mock(TradeDocumentService.class); }
        @Bean OrderReadScope scope() { return mock(OrderReadScope.class); }
        @Bean TradeDocumentController controller(TradeDocumentService s,FloworaAuthorization a,OrderReadScope scope) { return new TradeDocumentController(s,a,scope); }
    }
    @Autowired FloworaAuthorization auth;
    @Autowired TradeDocumentController controller;
    @Autowired TradeDocumentService documents;
    @Autowired OrderReadScope scope;
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final List<String> view=List.of("sales:view","procurement:view");
    final List<String> submit=List.of("sales:submit","procurement:submit");
    final List<String> both=List.of("sales:view","procurement:view","sales:submit","procurement:submit");
    @BeforeEach void resetState() { auth.setAuthenticator(null); reset(documents,scope); }
    @AfterEach void clearState() { SecurityContextHolder.clearContext(); }
    @Test void submitWithoutViewCannotReachScopeOrMutationEvenWithAdminRole() {
        denied(login(actor(DataScope.ALL,submit))); verifyNoInteractions(documents,scope);
    }
    @Test void readOnlyCannotReachScopeOrMutation() {
        denied(login(actor(DataScope.ALL,view))); verifyNoInteractions(documents,scope);
    }
    @Test void customCapabilityActionsCheckScopeBeforeEveryTransactionalCall() {
        var p=actor(DataScope.ALL,both); var a=login(p); actions(a);
        var order=inOrder(scope,documents);
        order.verify(scope).requireSales(p,"order"); order.verify(documents).confirmSalesOrder("org","order",7);
        order.verify(scope).requireSales(p,"order"); order.verify(documents).cancelSalesOrder("org","order",7);
        order.verify(scope).requirePurchase(p,"order"); order.verify(documents).confirmPurchaseOrder("org","order",7);
        order.verify(scope).requirePurchase(p,"order"); order.verify(documents).cancelPurchaseOrder("org","order",7);
        order.verifyNoMoreInteractions();
    }
    @Test void outOfScopeActionsCannotReachTransactionalServices() {
        doThrow(new AccessDeniedException("Outside scope")).when(scope).requireSales(any(),anyString());
        doThrow(new AccessDeniedException("Outside scope")).when(scope).requirePurchase(any(),anyString());
        for(var dataScope:List.of(DataScope.SELF,DataScope.DEPARTMENT,DataScope.ASSIGNED)) denied(login(actor(dataScope,both)));
        verifyNoInteractions(documents);
    }
    @Test void cachedAllIdentityIsReplacedBeforeScopeValidation() {
        var a=login(actor(DataScope.ALL,both)); var current=actor(DataScope.SELF,both);
        var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        when(resolver.principalForOrganization("user","org")).thenReturn(current);
        actions(a); verify(scope,times(2)).requireSales(current,"order"); verify(scope,times(2)).requirePurchase(current,"order");
    }
    @Test void revokedViewOrSubmitRejectsAnExistingAuthenticatedClient() {
        var a=login(actor(DataScope.ALL,both)); var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        for(var permissions:List.of(view,submit)) {
            when(resolver.principalForOrganization("user","org")).thenReturn(actor(DataScope.ALL,permissions)); denied(a);
        }
        verifyNoInteractions(documents,scope);
    }
    @Test void inactiveMembershipCannotReachScopeOrMutation() {
        var a=login(actor(DataScope.ALL,both)); var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        when(resolver.principalForOrganization("user","org")).thenThrow(new AccessDeniedException("Inactive member"));
        denied(a); verifyNoInteractions(documents,scope);
    }
    @Test void selfDraftCreationStillRequiresOnlyCreateCapability() {
        var p=actor(DataScope.SELF,List.of("sales:create","procurement:create")); var a=login(p);
        controller.createSalesOrder("key",null,a,request); controller.createPurchaseOrder("key",null,a,request);
        verify(documents).createSalesOrder(p,null,"key"); verify(documents).createPurchaseOrder(p,null,"key"); verifyNoInteractions(scope);
    }
    void actions(Authentication a) {
        controller.confirmSalesOrder("order",7,a,request); controller.cancelSalesOrder("order",7,a,request);
        controller.confirmPurchaseOrder("order",7,a,request); controller.cancelPurchaseOrder("order",7,a,request);
    }
    void denied(Authentication a) {
        assertThatThrownBy(()->controller.confirmSalesOrder("order",7,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.cancelSalesOrder("order",7,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.confirmPurchaseOrder("order",7,a,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->controller.cancelPurchaseOrder("order",7,a,request)).isInstanceOf(AccessDeniedException.class);
    }
    Authentication login(FloworaPrincipal p) {
        var a=UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()); SecurityContextHolder.getContext().setAuthentication(a); return a;
    }
    FloworaPrincipal actor(DataScope dataScope,List<String> permissions) {
        return new FloworaPrincipal("user","user@audit.invalid","Test","org","Org","member","department",dataScope,List.of("ADMIN","CUSTOM"),permissions,false);
    }
}
