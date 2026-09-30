package com.flowora.erp.search;

import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.identity.DataScope;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.project.ProjectRepository;
import com.flowora.erp.sales.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static com.flowora.erp.search.SearchDtos.SearchResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {
    @Mock
    private CustomerRepository customers;
    @Mock
    private SalesOrderRepository salesOrders;
    @Mock
    private ProjectRepository projects;

    @InjectMocks
    private GlobalSearchService service;

    @Test
    void scopesWorkspaceSearchToTheAuthenticatedOrganization() {
        when(customers.search(eq("org-a"), eq("Acme"), any())).thenReturn(new PageImpl<>(List.of()));
        when(salesOrders.scopedSearch(eq("org-a"), eq("Acme"), eq("ALL"), eq("user-a"), eq(null), any())).thenReturn(new PageImpl<>(List.of()));
        when(projects.scopedSearch(eq("org-a"), eq("Acme"), eq(null), eq("ALL"), eq("user-a"), eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        SearchResponse result = service.search(actor(List.of("master:view", "sales:view", "project:view")), " Acme ");

        assertThat(result.results()).isEmpty();
        verify(customers).search(eq("org-a"), eq("Acme"), any());
        verify(salesOrders).scopedSearch(eq("org-a"), eq("Acme"), eq("ALL"), eq("user-a"), eq(null), any());
        verify(projects).scopedSearch(eq("org-a"), eq("Acme"), eq(null), eq("ALL"), eq("user-a"), eq(null), any());
    }

    @Test
    void excludesModulesWithoutPermissionBeforeQueryingThem() {
        when(salesOrders.scopedSearch(eq("org-a"), eq("Acme"), eq("ALL"), eq("user-a"), eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        SearchResponse result = service.search(actor(List.of("sales:view")), "Acme");

        assertThat(result.results()).isEmpty();
        verifyNoInteractions(customers, projects);
    }

    private FloworaPrincipal actor(List<String> permissions) {
        return new FloworaPrincipal("user-a", "alice", "Alice", "org-a", "Org A", "membership-a",
                null, DataScope.ALL, List.of(), permissions, false);
    }
}
