package com.flowora.erp.system;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/system")
public class SystemVersionController {
    private final String productVersion;
    private final String deliveryStage;
    private final boolean v2BusinessWritesEnabled;

    public SystemVersionController(
            @Value("${flowora.release.version:2.0.0-SNAPSHOT}") String productVersion,
            @Value("${flowora.release.stage:M0}") String deliveryStage,
            @Value("${flowora.release.v2-business-writes-enabled:false}") boolean v2BusinessWritesEnabled
    ) {
        this.productVersion = productVersion;
        this.deliveryStage = deliveryStage;
        this.v2BusinessWritesEnabled = v2BusinessWritesEnabled;
    }

    @GetMapping("/version")
    public ApiResponse<VersionInfo> version(HttpServletRequest request) {
        return ApiResponse.of(new VersionInfo(
                productVersion,
                "v2",
                "v1",
                deliveryStage,
                v2BusinessWritesEnabled,
                List.of("v1-auth-session", "v1-lossless-read")
        ), RequestIdFilter.get(request));
    }

    public record VersionInfo(
            String productVersion,
            String apiVersion,
            String legacyApiVersion,
            String deliveryStage,
            boolean v2BusinessWritesEnabled,
            List<String> compatibility
    ) {
    }
}
