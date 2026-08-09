package com.flowora.erp.demo;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoDataController {
    private final DemoDataService service;

    public DemoDataController(DemoDataService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DemoDataStatus> status(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(service.status(principal(authentication)), RequestIdFilter.get(request));
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DemoDataStatus> reset(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(service.reset(principal(authentication), RequestIdFilter.get(request)), RequestIdFilter.get(request));
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) {
            return current;
        }
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }
}
