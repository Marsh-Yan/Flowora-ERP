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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformDirectoryServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyService passwordPolicy;
    @Mock
    private DatabaseAccountService accountService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PlatformDirectoryService service;

    @Test
    void preventsPasswordResetForAUserOutsideTheCurrentOrganization() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), eq("org-a"), eq("user-b")
        )).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("org-a", "user-b", "Temporary123!"))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("RESOURCE_NOT_FOUND"));
        verifyNoInteractions(accountService);
    }
}
