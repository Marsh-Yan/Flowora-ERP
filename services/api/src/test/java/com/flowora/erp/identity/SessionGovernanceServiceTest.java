package com.flowora.erp.identity;

import com.flowora.erp.common.api.PlatformApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionGovernanceServiceTest {
    private static final String USER = "capacity-test@example.invalid";
    private static final String KEY = "flowora:session-login:pending:" + USER;
    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<MapSession> repository = mock(FindByIndexNameSessionRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @SuppressWarnings("unchecked")
    private SessionGovernanceService service() {
        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositories = mock(ObjectProvider.class);
        ObjectProvider<StringRedisTemplate> stores = mock(ObjectProvider.class);
        doReturn(repository).when(repositories).getIfAvailable();
        when(stores.getIfAvailable()).thenReturn(redis);
        when(redis.opsForSet()).thenReturn(sets);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        return new SessionGovernanceService(repositories, stores);
    }

    @Test
    void completionRequiresThisExactLoginToBeIndexed() {
        var service = service();
        when(repository.findByPrincipalName(USER)).thenReturn(Map.of("other", new MapSession()));
        service.completeLogin(USER, "unsaved");
        verifyNoInteractions(sets);
        when(repository.findByPrincipalName(USER)).thenReturn(Map.of("saved", new MapSession()));
        service.completeLogin(USER, "saved");
        verify(sets).remove(KEY, "saved");
    }

    @Test
    void storeFailureLeavesTheBoundedReservation() {
        var service = service();
        when(repository.findByPrincipalName(USER)).thenThrow(new IllegalStateException("store unavailable"));
        assertThrows(IllegalStateException.class, () -> service.completeLogin(USER, "unsaved"));
        verifyNoInteractions(sets);
    }

    @Test
    void thirdActiveSessionPreventsAnotherReservation() {
        var service = service();
        when(repository.findByPrincipalName(USER)).thenReturn(Map.of("a", new MapSession(), "b", new MapSession(), "c", new MapSession()));
        when(sets.members(KEY)).thenReturn(new HashSet<>());
        var error = assertThrows(PlatformApiException.class, () -> service.reserveLogin(USER, "fourth"));
        assertEquals("SESSION_LIMIT_REACHED", error.code());
        verify(sets, never()).add(anyString(), any(String[].class));
    }

    @Test
    void inFlightReservationsStillEnforceTheThreeSessionLimit() {
        var service = service();
        when(repository.findByPrincipalName(USER)).thenReturn(Map.of());
        when(sets.members(KEY)).thenReturn(new HashSet<>(Set.of("a", "b", "c")));
        assertThrows(PlatformApiException.class, () -> service.reserveLogin(USER, "fourth"));
        verify(sets, never()).add(anyString(), any(String[].class));
    }

    @Test
    void persistedLoginIsCountedOnceAndTheNextSlotRemainsReserved() {
        var service = service();
        when(repository.findByPrincipalName(USER)).thenReturn(Map.of("a", new MapSession()));
        when(sets.members(KEY)).thenReturn(new HashSet<>(Set.of("a", "b")));
        service.reserveLogin(USER, "third");
        verify(sets).remove(KEY, "a");
        verify(sets).add(KEY, "third");
        verify(redis).expire(KEY, Duration.ofSeconds(30));
    }
}
