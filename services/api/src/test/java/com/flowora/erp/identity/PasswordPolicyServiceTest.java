package com.flowora.erp.identity;

import com.flowora.erp.common.api.PlatformApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyServiceTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordPolicyService service = new PasswordPolicyService(encoder);

    @Test
    void acceptsStrongPasswordOutsideHistory() {
        assertThatCode(() -> service.validate(
                "alex@example.com", "River!Stone-2048", List.of(encoder.encode("Previous!Pass-2047"))
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUsernameAndCommonPasswords() {
        assertThatThrownBy(() -> service.validate("alex@example.com", "Alex-is-admin-2026", List.of()))
                .isInstanceOf(PlatformApiException.class)
                .extracting("code").isEqualTo("PASSWORD_TOO_WEAK");
        assertThatThrownBy(() -> service.validate("alex@example.com", "password123", List.of()))
                .isInstanceOf(PlatformApiException.class);
    }

    @Test
    void rejectsRecentPasswordReuse() {
        String hash = encoder.encode("River!Stone-2048");
        assertThatThrownBy(() -> service.validate("alex@example.com", "River!Stone-2048", List.of(hash)))
                .isInstanceOf(PlatformApiException.class)
                .extracting("code").isEqualTo("PASSWORD_REUSED");
    }
}
