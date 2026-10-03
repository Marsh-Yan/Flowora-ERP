package com.flowora.erp.sales;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class QuoteValidityPolicy {
    private final Clock clock;

    public QuoteValidityPolicy() { this(Clock.systemUTC()); }
    public QuoteValidityPolicy(Clock clock) { this.clock = clock; }

    public LocalDate today(String timezone) {
        try {
            return LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        } catch (DateTimeException | NullPointerException error) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "ORGANIZATION_TIMEZONE_INVALID", "errors.organizationTimezoneInvalid");
        }
    }

    public boolean isCurrent(LocalDate validUntil, LocalDate today) {
        return validUntil != null && !validUntil.isBefore(today);
    }

    public void requireCurrent(LocalDate validUntil, LocalDate today) {
        if (!isCurrent(validUntil, today)) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "SOURCE_QUOTE_EXPIRED", "errors.sourceQuoteExpired");
        }
    }
}
