package com.flowora.erp.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String candidate = request.getHeader("X-Request-Id");
        String requestId = candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
                ? candidate
                : "req_" + UUID.randomUUID();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        filterChain.doFilter(request, response);
    }

    public static String get(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value == null ? "req_" + UUID.randomUUID() : value.toString();
    }
}
