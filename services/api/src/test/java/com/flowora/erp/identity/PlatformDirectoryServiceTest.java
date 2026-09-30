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

    @Test
    void rejectsArchivingAnOrganizationOutsideTheActiveMembership() {
        FloworaPrincipal actor = new FloworaPrincipal("user-a", "alice", "Alice", "org-a", "Org A",
                "membership-a", null, DataScope.ALL, List.of("ADMIN"),
                List.of("organization:configure"), false);

        assertThatThrownBy(() -> service.archiveOrganization(actor, "org-b"))
                .isInstanceOfSatisfying(PlatformApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ORGANIZATION_ACCESS_DENIED"));
        verifyNoInteractions(jdbcTemplate);
    }
}
