package com.flowora.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.ApiError;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class RequiredPasswordChangeFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/logout", "/api/v1/auth/csrf",
            "/api/v2/session/me", "/api/v2/session/logout", "/api/v2/session/csrf",
            "/api/v2/session/change-password"
    );
    private final ObjectMapper mapper;

    public RequiredPasswordChangeFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal principal
                && principal.mustChangePassword() && !ALLOWED.contains(request.getRequestURI())
                && request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(mapper.writeValueAsString(new ApiError(
                    "PASSWORD_CHANGE_REQUIRED", "errors.passwordChangeRequired", Map.of(), RequestIdFilter.get(request))));
            return;
        }
        chain.doFilter(request, response);
    }
}
