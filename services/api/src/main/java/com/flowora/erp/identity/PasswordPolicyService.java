package com.flowora.erp.identity;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PasswordPolicyService {
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "123456789012", "qwerty123456",
            "admin123456", "letmein123456", "welcome12345", "changeme1234"
    );

    private final PasswordEncoder passwordEncoder;

    public PasswordPolicyService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void validate(String username, String password, List<String> recentHashes) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            reject("PASSWORD_LENGTH_INVALID");
        }
        String normalizedPassword = password.toLowerCase(Locale.ROOT);
        String accountName = username == null ? "" : username.split("@", 2)[0].toLowerCase(Locale.ROOT);
        if ((!accountName.isBlank() && normalizedPassword.contains(accountName))
                || COMMON_PASSWORDS.contains(normalizedPassword)) {
            reject("PASSWORD_TOO_WEAK");
        }
        if (recentHashes.stream().anyMatch(hash -> passwordEncoder.matches(password, hash))) {
            reject("PASSWORD_REUSED");
        }
    }

    private void reject(String reason) {
        throw new PlatformApiException(HttpStatus.BAD_REQUEST, reason, "errors.passwordPolicy",
                Map.of("reason", reason));
    }
}
