package com.flowora.erp.search;

import com.flowora.erp.masterdata.CustomerRepository;
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
        when(salesOrders.search(eq("org-a"), eq("Acme"), any())).thenReturn(new PageImpl<>(List.of()));
        when(projects.search(eq("org-a"), eq("Acme"), eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        SearchResponse result = service.search("org-a", " Acme ");

        assertThat(result.results()).isEmpty();
        verify(customers).search(eq("org-a"), eq("Acme"), any());
        verify(salesOrders).search(eq("org-a"), eq("Acme"), any());
        verify(projects).search(eq("org-a"), eq("Acme"), eq(null), any());
    }
}
