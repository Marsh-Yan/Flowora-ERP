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
    public Enrollment startEnrollment(FloworaPrincipal principal) {
        String secret = totpService.newSecret();
        jdbcTemplate.update("DELETE FROM flowora_user_mfa WHERE user_id = ? AND factor_type = 'TOTP'", principal.userId());
        jdbcTemplate.update("""
                INSERT INTO flowora_user_mfa (
                    id, user_id, factor_type, secret_ciphertext, enabled
                ) VALUES (?, ?, 'TOTP', ?, FALSE)
                """, UUID.randomUUID().toString(), principal.userId(), cipher.encrypt(secret));
        String issuer = "Flowora ERP";
        String uri = "otpauth://totp/Flowora%20ERP:" + principal.username()
                + "?secret=" + secret + "&issuer=" + issuer.replace(" ", "%20") + "&digits=6&period=30";
        return new Enrollment(secret, uri);
    }

    @Transactional
    public List<String> confirmEnrollment(String userId, String code) {
        Map<String, Object> factor = factor(userId, false);
        if (!totpService.verify(cipher.decrypt((String) factor.get("secret_ciphertext")), code)) {
            invalidCode();
        }
        List<String> recoveryCodes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            String recoveryCode = recoveryCode();
            recoveryCodes.add(recoveryCode);
            hashes.add(passwordEncoder.encode(recoveryCode));
        }
        jdbcTemplate.update("""
                UPDATE flowora_user_mfa
                SET enabled = TRUE, verified_at = ?, recovery_codes_json = ?
                WHERE id = ?
                """, Timestamp.from(Instant.now()), json(hashes), factor.get("id"));
        return List.copyOf(recoveryCodes);
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
        verifyLogin(userId, code);
        jdbcTemplate.update("DELETE FROM flowora_user_mfa WHERE user_id = ? AND factor_type = 'TOTP'", userId);
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
