package com.flowora.erp.search;

import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.project.ProjectRepository;
import com.flowora.erp.sales.SalesOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.flowora.erp.search.SearchDtos.SearchResponse;
import static com.flowora.erp.search.SearchDtos.SearchResult;

@Service
public class GlobalSearchService {
    private final CustomerRepository customers;
    private final SalesOrderRepository salesOrders;
    private final ProjectRepository projects;

    public GlobalSearchService(CustomerRepository customers, SalesOrderRepository salesOrders, ProjectRepository projects) {
        this.customers = customers;
        this.salesOrders = salesOrders;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String organizationId, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) return new SearchResponse(List.of());
        PageRequest limit = PageRequest.of(0, 6);
        List<SearchResult> results = new ArrayList<>();
        customers.search(organizationId, normalized, limit).getContent().forEach(item -> results.add(new SearchResult("CUSTOMER", item.id(), item.name(), item.code(), "/settings")));
        salesOrders.search(organizationId, normalized, limit).getContent().forEach(item -> results.add(new SearchResult("SALES_ORDER", item.id(), item.number(), item.customerId(), "/sales")));
        projects.search(organizationId, normalized, null, limit).getContent().forEach(item -> results.add(new SearchResult("PROJECT", item.id(), item.name(), item.number(), "/projects")));
        return new SearchResponse(results.stream().limit(12).toList());
    }
}
