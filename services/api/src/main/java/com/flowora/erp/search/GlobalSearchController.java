package com.flowora.erp.search;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.search.SearchDtos.SearchResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/search", "/api/v2/search"})
public class GlobalSearchController {
    private final GlobalSearchService service;
    private final FloworaAuthorization authorization;

    public GlobalSearchController(GlobalSearchService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(defaultValue = "") String query, Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal principal = principal(authentication);
        return ApiResponse.of(service.search(principal, query), RequestIdFilter.get(request));
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }
}
