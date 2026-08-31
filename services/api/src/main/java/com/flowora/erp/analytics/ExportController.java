package com.flowora.erp.analytics;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.identity.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static com.flowora.erp.analytics.ExportDtos.*;

@RestController
@RequestMapping("/api/v2/exports")
public class ExportController {
    private final ExportJobService service;
    private final FloworaAuthorization authorization;
    private final SecurityAuditService audit;

    public ExportController(ExportJobService service, FloworaAuthorization authorization, SecurityAuditService audit) {
        this.service = service;
        this.authorization = authorization;
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:export')")
    public ApiResponse<ExportJobView> create(@Valid @RequestBody ExportCreate body, Authentication authentication,
                                             HttpServletRequest request) {
        FloworaPrincipal actor = principal(authentication);
        ExportJobView result = service.create(actor, body);
        audit.record(actor.userId(), actor.organizationId(), "SENSITIVE_EXPORT_REQUESTED", "SUCCESS", request,
                "{\"jobId\":\"" + result.id() + "\",\"resourceType\":\"" + result.resourceType() + "\"}");
        return ApiResponse.of(result, RequestIdFilter.get(request));
    }

    @GetMapping
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:export')")
    public ApiResponse<List<ExportJobView>> list(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(service.list(principal(authentication)), RequestIdFilter.get(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:export')")
    public ApiResponse<ExportJobView> get(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(service.find(principal(authentication), id), RequestIdFilter.get(request));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("@floworaAuthorization.has(authentication, 'analytics:export')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id, Authentication authentication,
                                                         HttpServletRequest request) throws IOException {
        FloworaPrincipal actor = principal(authentication);
        ExportDownload download = service.download(actor, id);
        audit.record(actor.userId(), actor.organizationId(), "SENSITIVE_EXPORT_DOWNLOADED", "SUCCESS", request,
                "{\"jobId\":\"" + id + "\",\"sha256\":\"" + download.sha256Hex() + "\"}");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .header("X-Content-SHA256", download.sha256Hex())
                .contentLength(Files.size(download.path()))
                .body(new InputStreamResource(Files.newInputStream(download.path())));
    }

    private FloworaPrincipal principal(Authentication authentication) { return authorization.principal(authentication); }
}
