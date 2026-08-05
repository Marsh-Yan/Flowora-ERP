package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.MasterDataDtos.OrganizationSettingsRequest;
import com.flowora.erp.masterdata.MasterDataDtos.OrganizationSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/settings")
public class OrganizationSettingsController {
    private final MasterDataService service;

    public OrganizationSettingsController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<OrganizationSettingsResponse> get(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(service.organizationSettings(organizationId(authentication)), RequestIdFilter.get(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<OrganizationSettingsResponse> update(
            @Valid @RequestBody OrganizationSettingsRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return ApiResponse.of(service.updateOrganizationSettings(organizationId(authentication), body), RequestIdFilter.get(request));
    }

    private String organizationId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal principal) {
            return principal.organizationId();
        }
        return "org-demo";
    }
}
