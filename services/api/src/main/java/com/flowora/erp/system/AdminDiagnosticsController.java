package com.flowora.erp.system;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/diagnostics")
@PreAuthorize("@floworaAuthorization.has(authentication, 'admin:diagnostics')")
public class AdminDiagnosticsController {
    private final DiagnosticsService service;

    public AdminDiagnosticsController(DiagnosticsService service) { this.service = service; }

    @GetMapping
    public ApiResponse<DiagnosticsService.DiagnosticSnapshot> diagnostics(HttpServletRequest request) {
        return ApiResponse.of(service.snapshot(), RequestIdFilter.get(request));
    }

    @GetMapping("/bundle")
    public ResponseEntity<byte[]> bundle(HttpServletRequest request) {
        String requestId = RequestIdFilter.get(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowora-diagnostics-" + requestId + ".json\"")
                .body(service.bundle(requestId));
    }
}
