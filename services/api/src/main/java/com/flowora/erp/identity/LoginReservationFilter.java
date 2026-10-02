package com.flowora.erp.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs outside Spring Session: its finally block sees the session saved at request completion. */
@Component
@Profile("local | production")
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
public class LoginReservationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(LoginReservationFilter.class);
    private static final String RESERVATION = LoginReservationFilter.class.getName() + ".reservation";
    private final SessionGovernanceService governance;

    public LoginReservationFilter(SessionGovernanceService governance) {
        this.governance = governance;
    }

    static void track(HttpServletRequest request, String username, String sessionId) {
        request.setAttribute(RESERVATION, new Reservation(username, sessionId));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getAttribute(RESERVATION) instanceof Reservation reservation) {
                try {
                    governance.completeLogin(reservation.username(), reservation.sessionId());
                } catch (RuntimeException exception) {
                    // Retain the bounded reservation on store failure; do not hide the original response/error.
                    LOG.warn("Login reservation cleanup could not confirm the indexed session; bounded TTL retained");
                }
            }
        }
    }

    private record Reservation(String username, String sessionId) {}
}
