package com.flowora.erp.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class MasterDataDtos {
    private MasterDataDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String contactName,
            @Email @Size(max = 190) String email,
            @Size(max = 48) String phone,
            @Size(max = 500) String address,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @PositiveOrZero @Max(3650) int paymentTermsDays,
            boolean active
    ) {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String contactName,
            @Email @Size(max = 190) String email,
            @Size(max = 48) String phone,
            @Size(max = 500) String address,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @PositiveOrZero @Max(3650) int paymentTermsDays,
            boolean active
    ) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull ItemType type,
            @NotBlank @Size(max = 24) String unit,
            @NotNull @DecimalMin("0.0") BigDecimal salesPrice,
            @NotNull @DecimalMin("0.0") BigDecimal purchasePrice,
            @NotNull @DecimalMin("0.0") BigDecimal averageCost,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal taxRate,
            boolean inventoryManaged,
            boolean active
    ) {
    }

    public record WarehouseRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String address,
            boolean active
    ) {
    }

    public record CurrencyRequest(
            @NotBlank @Size(min = 3, max = 3) String code,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 8) String symbol,
            @Min(0) @Max(6) int decimalPlaces,
            boolean active
    ) {
    }

    public record ExchangeRateRequest(
            @NotBlank @Size(min = 3, max = 3) String baseCurrencyCode,
            @NotBlank @Size(min = 3, max = 3) String quoteCurrencyCode,
            @NotNull @DecimalMin("0.00000001") BigDecimal rate,
            @NotNull LocalDate effectiveDate,
            boolean active
    ) {
    }

    public record TaxRateRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal rate,
            boolean exempt,
            @NotNull LocalDate effectiveDate,
            boolean active
    ) {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 48) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull AccountType type,
            @Size(max = 48) String parentCode,
            boolean postingAllowed,
            boolean active
    ) {
    }

    public record OrganizationSettingsRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(min = 3, max = 3) String baseCurrencyCode,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull @PositiveOrZero BigDecimal approvalThreshold,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal defaultTaxRate
    ) {
    }

    public record CustomerResponse(
            String id, String code, String name, String contactName, String email, String phone,
            String address, String currencyCode, int paymentTermsDays, boolean active
    ) {
    }

    public record SupplierResponse(
            String id, String code, String name, String contactName, String email, String phone,
            String address, String currencyCode, int paymentTermsDays, boolean active
    ) {
    }

    public record ItemResponse(
            String id, String code, String name, ItemType type, String unit, BigDecimal salesPrice,
            BigDecimal purchasePrice, BigDecimal averageCost, BigDecimal taxRate, boolean inventoryManaged, boolean active
    ) {
    }

    public record WarehouseResponse(String id, String code, String name, String address, boolean active) {
    }

    public record CurrencyResponse(String id, String code, String name, String symbol, int decimalPlaces, boolean active) {
    }

    public record ExchangeRateResponse(
            String id, String baseCurrencyCode, String quoteCurrencyCode, BigDecimal rate, LocalDate effectiveDate, boolean active
    ) {
    }

    public record TaxRateResponse(
            String id, String code, String name, BigDecimal rate, boolean exempt, LocalDate effectiveDate, boolean active
    ) {
    }

    public record AccountResponse(
            String id, String code, String name, AccountType type, String parentCode, boolean postingAllowed, boolean active
    ) {
    }

    public record OrganizationSettingsResponse(
            String id, String name, String baseCurrencyCode, String timezone,
            BigDecimal approvalThreshold, BigDecimal defaultTaxRate, boolean active
    ) {
    }

    public record ImportResult(int imported, int rejected, List<Map<String, Object>> errors) {
    }
}
