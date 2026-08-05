package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final DemoUserStore userStore;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(DemoUserStore userStore) {
        this.userStore = userStore;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token, HttpServletRequest request) {
        return ApiResponse.of(Map.of("token", token.getToken()), RequestIdFilter.get(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        FloworaPrincipal principal = userStore.authenticate(request.username(), request.password());
        Authentication authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return ApiResponse.of(AuthUserResponse.from(principal), RequestIdFilter.get(httpRequest));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.of(AuthUserResponse.from((FloworaPrincipal) authentication.getPrincipal()), RequestIdFilter.get(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Boolean>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ApiResponse.of(Map.of("authenticated", false), RequestIdFilter.get(request));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AuthUserResponse(
            String id,
            String username,
            String displayName,
            String organizationId,
            String organizationName,
            List<String> roles
    ) {
        static AuthUserResponse from(FloworaPrincipal principal) {
            return new AuthUserResponse(
                    principal.userId(),
                    principal.username(),
                    principal.displayName(),
                    principal.organizationId(),
                    principal.organizationName(),
                    principal.roles()
            );
        }
    }
}
