package com.flowora.erp.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.web.http.SessionRepositoryFilter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginReservationFilterTest {
    private static final String USER = "reservation-test@example.invalid";
    private static final String KEY = "flowora:session-login:pending:" + USER;

    @SuppressWarnings("unchecked")
    @Test
    void retainsReservationUntilSpringSessionSavesThenReleasesOnlyItsSlot() throws Exception {
        FindByIndexNameSessionRepository<MapSession> repository = mock(FindByIndexNameSessionRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        Map<String, MapSession> indexed = new HashMap<>();
        MapSession session = new MapSession();
        when(repository.createSession()).thenReturn(session);
        when(repository.findByPrincipalName(USER)).thenAnswer(invocation -> indexed);
        doAnswer(invocation -> {
            MapSession saved = invocation.getArgument(0);
            if (USER.equals(saved.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)))
                indexed.put(saved.getId(), new MapSession(saved));
            return null;
        }).when(repository).save(any());
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositories = mock(ObjectProvider.class);
        ObjectProvider<StringRedisTemplate> stores = mock(ObjectProvider.class);
        doReturn(repository).when(repositories).getIfAvailable();
        when(stores.getIfAvailable()).thenReturn(redis);
        var governance = new SessionGovernanceService(repositories, stores);
        var outer = new LoginReservationFilter(governance);
        var springSession = new SessionRepositoryFilter<>(repository);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        outer.doFilter(request, response, (req, res) -> springSession.doFilter(req, res, (wrapped, output) -> {
            var http = (jakarta.servlet.http.HttpServletRequest) wrapped;
            var current = http.getSession(true);
            current.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, USER);
            LoginReservationFilter.track(http, USER, current.getId());
            assertTrue(indexed.isEmpty());
            verifyNoInteractions(sets); // No capacity gap while the authenticated session is still unsaved.
        }));
        assertEquals(1, indexed.size());
        verify(sets).remove(KEY, session.getId());
    }

    @Test
    void failedOrUnrelatedRequestDoesNotRegisterCleanup() throws Exception {
        var governance = mock(SessionGovernanceService.class);
        new LoginReservationFilter(governance).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {});
        verifyNoInteractions(governance);
    }

    @Test
    void cleanupFailureDoesNotReplaceTheOriginalRequestFailure() {
        var governance = mock(SessionGovernanceService.class);
        doThrow(new IllegalStateException("store unavailable")).when(governance).completeLogin(USER, "saved-id");
        var request = new MockHttpServletRequest();
        var original = new jakarta.servlet.ServletException("original request failure");
        var actual = assertThrows(jakarta.servlet.ServletException.class, () ->
                new LoginReservationFilter(governance).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    LoginReservationFilter.track(request, USER, "saved-id");
                    throw original;
                }));
        assertSame(original, actual);
    }

    @Test
    void cleanupDoesNotRunWhenTheResponseIsFlushedUntilTheInnerFilterReturns() throws Exception {
        var governance = mock(SessionGovernanceService.class);
        var request = new MockHttpServletRequest();
        new LoginReservationFilter(governance).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            LoginReservationFilter.track(request, USER, "saved-id");
            res.flushBuffer();
            verifyNoInteractions(governance);
        });
        verify(governance).completeLogin(USER, "saved-id");
    }

    @Test
    void runsOutsideTheSessionRepositoryFilter() {
        assertTrue(LoginReservationFilter.class.getAnnotation(Order.class).value() < SessionRepositoryFilter.DEFAULT_ORDER);
    }
}
