package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v2/masters")
@Profile("local | production")
public class MasterDataPlatformController {
    private final MasterDataPlatformService service;
    private final FloworaAuthorization authorization;

    public MasterDataPlatformController(MasterDataPlatformService service, FloworaAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/locations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:view')")
    public ApiResponse<List<MasterDataPlatformService.LocationView>> locations(
            @RequestParam(required = false) String warehouseId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.locations(principal(authentication).organizationId(), warehouseId), request);
    }

    @PostMapping("/locations")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:create')")
    public ApiResponse<MasterDataPlatformService.LocationView> createLocation(
            @Valid @RequestBody LocationRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.createLocation(principal(authentication).organizationId(), body.toCommand()), request);
    }

    @PutMapping("/locations/{locationId}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:edit')")
    public ApiResponse<MasterDataPlatformService.LocationView> updateLocation(
            @PathVariable String locationId,
            @RequestHeader("If-Match") long expectedVersion,
            @Valid @RequestBody LocationUpdateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.updateLocation(
                principal(authentication).organizationId(), locationId, expectedVersion,
                body.toCommand(), body.active()), request);
    }

    @PutMapping("/items/{itemId}/tracking")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:edit')")
    public ApiResponse<MasterDataPlatformService.ItemTrackingView> updateItemTracking(
            @PathVariable String itemId,
            @RequestHeader("If-Match") long expectedVersion,
            @Valid @RequestBody TrackingRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.updateItemTracking(
                principal(authentication).organizationId(), itemId, expectedVersion, body.trackingMethod()), request);
    }

    @GetMapping("/tax-rules")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:view')")
    public ApiResponse<List<MasterDataPlatformService.TaxRuleView>> taxRules(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.taxRules(principal(authentication).organizationId()), request);
    }

    @PostMapping("/tax-rules")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'master:configure')")
    public ApiResponse<MasterDataPlatformService.TaxRuleView> createTaxRule(
            @Valid @RequestBody TaxRuleRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.createTaxRule(principal(authentication).organizationId(), body.toCommand()), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }

    public record LocationRequest(
            @NotBlank String warehouseId,
            String parentId,
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull LocationType locationType
    ) {
        MasterDataPlatformService.LocationCommand toCommand() {
            return new MasterDataPlatformService.LocationCommand(warehouseId, parentId, code, name, locationType);
        }
    }

    public record LocationUpdateRequest(
            @NotBlank String warehouseId,
            String parentId,
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull LocationType locationType,
            boolean active
    ) {
        MasterDataPlatformService.LocationCommand toCommand() {
            return new MasterDataPlatformService.LocationCommand(warehouseId, parentId, code, name, locationType);
        }
    }

    public record TrackingRequest(@NotNull TrackingMethod trackingMethod) {}

    public record TaxComponentRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal rate,
            boolean compound
    ) {
        MasterDataPlatformService.TaxComponentCommand toCommand() {
            return new MasterDataPlatformService.TaxComponentCommand(code, name, rate, compound);
        }
    }

    public record TaxRuleRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @NotEmpty @Valid List<TaxComponentRequest> components
    ) {
        MasterDataPlatformService.TaxRuleCommand toCommand() {
            return new MasterDataPlatformService.TaxRuleCommand(
                    code, name, components.stream().map(TaxComponentRequest::toCommand).toList()
            );
        }
    }
}
