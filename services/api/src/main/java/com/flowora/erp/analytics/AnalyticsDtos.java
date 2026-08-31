package com.flowora.erp.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record WorkspaceCard(String code, BigDecimal value, String currencyCode,
                                String route, String requiredPermission, String severity) {
    }

    public record WorkspaceSnapshot(String organizationId, List<String> roles, Instant refreshedAt,
                                    List<WorkspaceCard> cards, List<String> risks) {
    }

    public record TrendPoint(String period, BigDecimal sales, BigDecimal purchases,
                             BigDecimal revenue, BigDecimal expense, BigDecimal grossProfit) {
    }

    public record AnalyticsSnapshot(LocalDate from, LocalDate to, String currencyCode,
                                    List<TrendPoint> trends, Instant refreshedAt) {
    }

    public record OrganizationSummary(String organizationId, String organizationName,
                                      String sourceCurrencyCode, String reportCurrencyCode,
                                      LocalDate exchangeRateDate, boolean exchangeRateMissing,
                                      BigDecimal sales, BigDecimal receivables, BigDecimal payables,
                                      BigDecimal cash, boolean includesEliminations) {
    }

    public record SavedViewCreate(@NotBlank @Size(max = 64) String resourceType,
                                  @NotBlank @Size(max = 120) String name,
                                  Map<String, Object> definition,
                                  boolean shared) {
    }

    public record SavedView(String id, String resourceType, String name, Map<String, Object> definition,
                            boolean shared, String ownerUserId, Instant updatedAt, long version) {
    }
}
