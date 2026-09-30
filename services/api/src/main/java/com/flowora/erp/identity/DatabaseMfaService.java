package com.flowora.erp.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("local | production")
public class DatabaseMfaService {
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final JdbcTemplate jdbcTemplate;
    private final TotpService totpService;
    private final MfaSecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public DatabaseMfaService(
            JdbcTemplate jdbcTemplate,
            TotpService totpService,
            MfaSecretCipher cipher,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.totpService = totpService;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Enrollment startEnrollment(FloworaPrincipal principal, String currentCode) {
        lockUser(principal.userId());
        if (required(principal.userId())) {
            if (currentCode == null || currentCode.isBlank()) invalidCode();
            verifyLogin(principal.userId(), currentCode);
        }
        String secret = totpService.newSecret();
        jdbcTemplate.update("""
                INSERT INTO flowora_pending_mfa_enrollment (user_id, secret_ciphertext, expires_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE secret_ciphertext = VALUES(secret_ciphertext),
                                        expires_at = VALUES(expires_at), created_at = CURRENT_TIMESTAMP
                """, principal.userId(), cipher.encrypt(secret), Timestamp.from(Instant.now().plusSeconds(600)));
        String issuer = "Flowora ERP";
        String uri = "otpauth://totp/Flowora%20ERP:" + principal.username()
                + "?secret=" + secret + "&issuer=" + issuer.replace(" ", "%20") + "&digits=6&period=30";
        return new Enrollment(secret, uri);
    }

    @Transactional
    public List<String> confirmEnrollment(String userId, String code) {
        lockUser(userId);
        List<Map<String, Object>> pending = jdbcTemplate.queryForList("""
                SELECT secret_ciphertext FROM flowora_pending_mfa_enrollment
                WHERE user_id = ? AND expires_at > ? FOR UPDATE
                """, userId, Timestamp.from(Instant.now()));
        if (pending.isEmpty()) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "MFA_ENROLLMENT_EXPIRED", "errors.mfaNotConfigured");
        }
        String encryptedSecret = (String) pending.getFirst().get("secret_ciphertext");
        if (!totpService.verify(cipher.decrypt(encryptedSecret), code)) {
            invalidCode();
        }
        List<String> recoveryCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            String recoveryCode = recoveryCode();
            recoveryCodes.add(recoveryCode);
            hashes.add(passwordEncoder.encode(recoveryCode));
        }
        jdbcTemplate.update("DELETE FROM flowora_user_mfa WHERE user_id = ? AND factor_type = 'TOTP'", userId);
        jdbcTemplate.update("""
                INSERT INTO flowora_user_mfa
                    (id, user_id, factor_type, secret_ciphertext, enabled, verified_at, recovery_codes_json)
                VALUES (?, ?, 'TOTP', ?, TRUE, ?, ?)
                """, UUID.randomUUID().toString(), userId, encryptedSecret, Timestamp.from(Instant.now()), json(hashes));
        jdbcTemplate.update("DELETE FROM flowora_pending_mfa_enrollment WHERE user_id = ?", userId);
        return List.copyOf(recoveryCodes);
    }

    @Transactional
    public void cancelEnrollment(String userId) {
        jdbcTemplate.update("DELETE FROM flowora_pending_mfa_enrollment WHERE user_id = ?", userId);
    }

    @Transactional(readOnly = true)
    public boolean required(String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flowora_user_mfa
                WHERE user_id = ? AND factor_type = 'TOTP' AND enabled = TRUE
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    @Transactional
    public void verifyLogin(String userId, String code) {
        Map<String, Object> factor = factor(userId, true);
        String secret = cipher.decrypt((String) factor.get("secret_ciphertext"));
        if (totpService.verify(secret, code)) return;
        List<String> hashes = readHashes((String) factor.get("recovery_codes_json"));
        for (int index = 0; index < hashes.size(); index++) {
            if (passwordEncoder.matches(code == null ? "" : code.trim().toUpperCase(), hashes.get(index))) {
                hashes.remove(index);
                jdbcTemplate.update("UPDATE flowora_user_mfa SET recovery_codes_json = ? WHERE id = ?",
                        json(hashes), factor.get("id"));
                return;
            }
        }
        invalidCode();
    }

    @Transactional
    public void disable(String userId, String code) {
        lockUser(userId);
        verifyLogin(userId, code);
        jdbcTemplate.update("DELETE FROM flowora_user_mfa WHERE user_id = ? AND factor_type = 'TOTP'", userId);
        jdbcTemplate.update("DELETE FROM flowora_pending_mfa_enrollment WHERE user_id = ?", userId);
    }

    private void lockUser(String userId) {
        jdbcTemplate.queryForObject("SELECT id FROM flowora_user_account WHERE id = ? FOR UPDATE", String.class, userId);
    }

    private Map<String, Object> factor(String userId, boolean enabled) {
        List<Map<String, Object>> factors = jdbcTemplate.queryForList("""
                SELECT id, secret_ciphertext, recovery_codes_json
                FROM flowora_user_mfa
                WHERE user_id = ? AND factor_type = 'TOTP' AND enabled = ?
                FOR UPDATE
                """, userId, enabled);
        if (factors.isEmpty()) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "MFA_NOT_CONFIGURED", "errors.mfaNotConfigured");
        }
        return factors.getFirst();
    }

    private List<String> readHashes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid MFA recovery-code data", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize MFA data", exception);
        }
    }

    private String recoveryCode() {
        StringBuilder code = new StringBuilder(19);
        for (int index = 0; index < 16; index++) {
            if (index > 0 && index % 4 == 0) code.append('-');
            code.append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }

    private void invalidCode() {
        throw new PlatformApiException(HttpStatus.UNAUTHORIZED, "MFA_CODE_INVALID", "errors.mfaCodeInvalid");
    }

    public record Enrollment(String secret, String otpauthUri) {}
}
