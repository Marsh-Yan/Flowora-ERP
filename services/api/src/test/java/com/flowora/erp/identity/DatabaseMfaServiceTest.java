package com.flowora.erp.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseMfaServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock TotpService totp;
    @Mock MfaSecretCipher cipher;
    @Mock PasswordEncoder passwords;
    @Mock ObjectMapper mapper;
    @InjectMocks DatabaseMfaService service;

    private final FloworaPrincipal actor = new FloworaPrincipal("user-a", "alice", "Alice",
            "org-a", "Org A", List.of("ADMIN"));

    @Test
    void startingReplacementWithoutCurrentFactorLeavesItEnabled() {
        when(jdbc.queryForObject(startsWith("SELECT id FROM flowora_user_account"),
                eq(String.class), eq("user-a"))).thenReturn("user-a");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("user-a"))).thenReturn(1);

        assertThatThrownBy(() -> service.startEnrollment(actor, null))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MFA_CODE_INVALID"));
        verify(jdbc, never()).update(startsWith("DELETE FROM flowora_user_mfa"), (Object[]) any());
    }

    @Test
    void invalidPendingCodeCannotReplaceCurrentFactor() {
        when(jdbc.queryForObject(startsWith("SELECT id FROM flowora_user_account"),
                eq(String.class), eq("user-a"))).thenReturn("user-a");
        when(jdbc.queryForList(anyString(), eq("user-a"), any())).thenReturn(
                List.of(Map.of("secret_ciphertext", "encrypted")));
        when(cipher.decrypt("encrypted")).thenReturn("secret");
        when(totp.verify("secret", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmEnrollment("user-a", "000000"))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MFA_CODE_INVALID"));
        verify(jdbc, never()).update(startsWith("DELETE FROM flowora_user_mfa"), (Object[]) any());
    }
}
