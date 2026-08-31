package com.flowora.erp.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

public class LegacyApiReadOnlyFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public LegacyApiReadOnlyFilter(ObjectMapper objectMapper, boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled || !path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/")
                || HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(426);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Upgrade", "Flowora-API/2");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                "API_VERSION_READ_ONLY", "errors.apiVersionReadOnly",
                Map.of("replacement", replacement(request.getRequestURI())),
                RequestIdFilter.get(request)
        ));
    }

    private String replacement(String path) {
        Map<String, String> bases = Map.ofEntries(
                Map.entry("/api/v1/finance", "/api/v2/compat/finance"),
                Map.entry("/api/v1/procurement", "/api/v2/compat/procurement"),
                Map.entry("/api/v1/inventory", "/api/v2/compat/inventory"),
                Map.entry("/api/v1/projects", "/api/v2/compat/projects"),
                Map.entry("/api/v1/sales", "/api/v2/compat/sales"),
                Map.entry("/api/v1/workflow", "/api/v2/compat/workflow"),
                Map.entry("/api/v1/masters", "/api/v2/masters"),
                Map.entry("/api/v1/organizations", "/api/v2/organizations"),
                Map.entry("/api/v1/demo", "/api/v2/demo"),
                Map.entry("/api/v1/search", "/api/v2/search")
        );
        for (Map.Entry<String, String> entry : bases.entrySet()) {
            if (path.startsWith(entry.getKey())) return entry.getValue() + path.substring(entry.getKey().length());
        }
        return path.replaceFirst("/api/v1/", "/api/v2/");
    }
}
