package com.flowora.erp.system;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        return ApiResponse.of(Map.of(
                "service", "flowora-api",
                "status", "UP"
        ), RequestIdFilter.get(request));
    }
}
