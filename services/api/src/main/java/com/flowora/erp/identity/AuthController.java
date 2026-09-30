package com.flowora.erp.identity;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
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
    private final SessionV2Controller sessions;

    public AuthController(SessionV2Controller sessions) {
        this.sessions = sessions;
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
        SessionV2Controller.SessionUser user = sessions.login(
                new SessionV2Controller.LoginRequest(request.username(), request.password(), request.mfaCode()),
                httpRequest, httpResponse).data();
        return ApiResponse.of(AuthUserResponse.from(user), RequestIdFilter.get(httpRequest));
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

    public record LoginRequest(@NotBlank String username, @NotBlank String password, String mfaCode) {
    }

    public record AuthUserResponse(
            String id,
            String username,
            String displayName,
            String organizationId,
            String organizationName,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword
    ) {
        static AuthUserResponse from(FloworaPrincipal principal) {
            return new AuthUserResponse(
                    principal.userId(),
                    principal.username(),
                    principal.displayName(),
                    principal.organizationId(),
                    principal.organizationName(),
                    principal.roles(),
                    principal.permissions(),
                    principal.mustChangePassword()
            );
        }
        static AuthUserResponse from(SessionV2Controller.SessionUser user) {
            return new AuthUserResponse(user.id(), user.username(), user.displayName(), user.organizationId(),
                    user.organizationName(), user.roles(), user.permissions(), user.mustChangePassword());
        }
    }
}
