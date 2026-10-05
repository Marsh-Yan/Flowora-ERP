package com.flowora.erp.identity;

import com.flowora.erp.finance.AccountingService;
import com.flowora.erp.finance.FinanceController;
import com.flowora.erp.finance.v2.*;
import com.flowora.erp.inventory.InventoryController;
import com.flowora.erp.inventory.InventoryService;
import com.flowora.erp.trade.v2.TradeInventoryController;
import com.flowora.erp.trade.v2.TradeInventoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real method-security proxies, including legacy role authorities and live identity changes. */
@SpringJUnitConfig(OrganizationModuleAuthorizationTest.Config.class)
class OrganizationModuleAuthorizationTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean FloworaAuthorization floworaAuthorization() { return new FloworaAuthorization(); }
        @Bean InventoryService inventoryService() { return mock(InventoryService.class); }
        @Bean AccountingService accountingService() { return mock(AccountingService.class); }
        @Bean TradeInventoryService tradeInventoryService() { return mock(TradeInventoryService.class); }
        @Bean FinanceDocumentService documents() { return mock(FinanceDocumentService.class); }
        @Bean FinanceLedgerService ledger() { return mock(FinanceLedgerService.class); }
        @Bean FinanceOperationsService operations() { return mock(FinanceOperationsService.class); }
        @Bean InventoryController inventory(InventoryService s, FloworaAuthorization a) { return new InventoryController(s,a); }
        @Bean FinanceController finance(AccountingService s, FloworaAuthorization a) { return new FinanceController(s,a); }
        @Bean TradeInventoryController nativeInventory(TradeInventoryService s, FloworaAuthorization a) { return new TradeInventoryController(s,a); }
        @Bean FinanceV2Controller nativeFinance(FinanceDocumentService d, FinanceLedgerService l, FinanceOperationsService o, FloworaAuthorization a) { return new FinanceV2Controller(d,l,o,a); }
    }
    @Autowired FloworaAuthorization auth;
    @Autowired InventoryController inventory;
    @Autowired FinanceController finance;
    @Autowired TradeInventoryController nativeInventory;
    @Autowired FinanceV2Controller nativeFinance;
    @Autowired InventoryService stock;
    @Autowired AccountingService accounting;
    @Autowired TradeInventoryService trade;
    @Autowired FinanceDocumentService documents;
    @Autowired FinanceOperationsService operations;
    final MockHttpServletRequest request = new MockHttpServletRequest();
    @BeforeEach void resetContext() { auth.setAuthenticator(null); clearInvocations(stock,accounting,trade,documents); }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    @Test void administrativeRoleNamesCannotReplaceActualPostingPermission() {
        var authentication = login(DataScope.ALL,List.of("ADMIN","FINANCE","WAREHOUSE"),List.of("finance:view","inventory:view"));
        assertThatThrownBy(()->finance.manual(null,authentication,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->inventory.count(null,authentication,request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->inventory.approveAdjustment("id",authentication,request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stock,accounting);
    }
    @Test void customPostingPermissionWorksWithoutFixedRoleNames() {
        var authentication = login(DataScope.ALL,List.of("CUSTOM"),List.of("finance:post","inventory:post"));
        finance.manual(null,authentication,request); inventory.count(null,authentication,request);
        verify(accounting).manual((FloworaPrincipal)authentication.getPrincipal(),null);
        verify(stock).count((FloworaPrincipal)authentication.getPrincipal(),null);
    }
    @Test void adjustmentApprovalRequiresPostingAndWorkflowApprovalTogether() {
        for(var permissions:List.of(List.of("inventory:post"),List.of("workflow:approve"))) {
            var a=login(DataScope.ALL,List.of("ADMIN"),permissions);
            assertThatThrownBy(()->inventory.approveAdjustment("id",a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->inventory.rejectAdjustment("id",a,request)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(stock);
        var a=login(DataScope.ALL,List.of("CUSTOM"),List.of("inventory:post","workflow:approve"));
        inventory.approveAdjustment("id",a,request);
        verify(stock).approveAdjustment(eq((FloworaPrincipal)a.getPrincipal()),eq("id"),anyString());
    }
    @Test void unsupportedScopesCannotReadOrPostSharedOrganizationData() {
        for(var scope:List.of(DataScope.SELF,DataScope.DEPARTMENT,DataScope.ASSIGNED)) {
            var a=login(scope,List.of("ADMIN"),List.of("finance:view","finance:post","inventory:view","inventory:post"));
            assertThatThrownBy(()->finance.incomeStatement(LocalDate.now(),LocalDate.now(),a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->nativeFinance.invoices("","",a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->nativeInventory.availability("","",a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->inventory.count(null,a,request)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->inventory.summary(a,request)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(stock,accounting,trade,documents);
    }
    @Test void inventorySummaryRequiresActualViewPermissionAndUsesFreshOrganization() {
        var denied = login(DataScope.ALL,List.of("ADMIN"),List.of());
        assertThatThrownBy(()->inventory.summary(denied,request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(stock);
        var a = login(DataScope.ALL,List.of("CUSTOM"),List.of("inventory:view"));
        inventory.summary(a,request); verify(stock).summary("org");
        var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        when(resolver.principalForOrganization("user","org")).thenReturn(actor(DataScope.ALL,List.of("ADMIN"),List.of()));
        final var existingSession=a;
        assertThatThrownBy(()->inventory.summary(existingSession,request)).isInstanceOf(AccessDeniedException.class);
        verify(stock,times(1)).summary("org");
    }
    @Test void compatibilityServicesReceiveFreshIdentityAndRevocationsTakeEffect() {
        var a=login(DataScope.ALL,List.of("CUSTOM"),List.of("finance:post","inventory:post"));
        var resolver=mock(DatabaseIdentityAuthenticator.class); auth.setAuthenticator(resolver);
        var fresh=actor(DataScope.ALL,List.of("CUSTOM"),List.of("finance:post","inventory:post"));
        when(resolver.principalForOrganization("user","org")).thenReturn(fresh);
        finance.manual(null,a,request); verify(accounting).manual(fresh,null);
        when(resolver.principalForOrganization("user","org")).thenReturn(actor(DataScope.ALL,List.of("ADMIN"),List.of()));
        assertThatThrownBy(()->finance.manual(null,a,request)).isInstanceOf(AccessDeniedException.class);
        when(resolver.principalForOrganization("user","org")).thenReturn(actor(DataScope.SELF,List.of("ADMIN"),List.of("inventory:post")));
        assertThatThrownBy(()->inventory.count(null,a,request)).isInstanceOf(AccessDeniedException.class);
        verify(accounting,times(1)).manual(any(),any()); verifyNoInteractions(stock);
    }
    @Test void bankAccountPickerRequiresFinanceViewAndAllScope() {
        var allowed=login(DataScope.ALL,List.of("CUSTOM"),List.of("finance:view"));
        nativeFinance.bankAccounts(allowed,request); verify(operations).bankAccounts("org");
        for (DataScope scope : List.of(DataScope.SELF,DataScope.DEPARTMENT,DataScope.ASSIGNED)) {
            var scoped=login(scope,List.of("ADMIN"),List.of("finance:view"));
            assertThatThrownBy(()->nativeFinance.bankAccounts(scoped,request)).isInstanceOf(AccessDeniedException.class);
        }
        var denied=login(DataScope.ALL,List.of("ADMIN"),List.of());
        assertThatThrownBy(()->nativeFinance.bankAccounts(denied,request)).isInstanceOf(AccessDeniedException.class);
        verify(operations,times(1)).bankAccounts("org");
    }
    Authentication login(DataScope scope,List<String> roles,List<String> permissions) {
        var actor=actor(scope,roles,permissions);
        var a=UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(a); return a;
    }
    FloworaPrincipal actor(DataScope scope,List<String> roles,List<String> permissions) {
        return new FloworaPrincipal("user","user@audit.invalid","Tester","org","Org","membership",null,scope,roles,permissions,false);
    }
}
