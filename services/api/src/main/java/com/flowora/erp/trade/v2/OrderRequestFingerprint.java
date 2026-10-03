package com.flowora.erp.trade.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.security.MessageDigest;
import java.util.HexFormat;
import static com.flowora.erp.trade.v2.TradeDocumentDtos.*;

/** Immutable create input; line order is not stored and does not change business meaning. */
final class OrderRequestFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();

    record Payload(String partner, String warehouse, String currency, LocalDate date, String note, List<LineRequest> lines) {
        static Payload of(SalesOrderRequest request) {
            return new Payload(request.customerId(), request.warehouseId(), request.currencyCode(), request.dueDate(), request.note(), request.lines());
        }
        static Payload of(PurchaseOrderRequest request) {
            return new Payload(request.supplierId(), request.warehouseId(), request.currencyCode(), request.expectedDate(), request.note(), request.lines());
        }
    }

    static String fingerprint(Payload request, boolean storagePrecision) {
        List<String> lines = request.lines().stream().map(line -> {
            var amount = TradeAmountPolicy.calculate(line.quantity(), line.unitPrice(), line.discountRate(), line.taxRate());
            return line(line, amount, storagePrecision);
        }).sorted().toList();
        return header(request.partner(), request.warehouse(), request.currency(), request.date(), request.note(), lines);
    }

    static String line(LineRequest line, TradeAmountPolicy.Amounts amounts, boolean storagePrecision) {
        return json(Arrays.asList(line.itemId(), decimal(line.quantity(), storagePrecision),
                decimal(line.unitPrice(), storagePrecision), decimal(line.discountRate(), storagePrecision),
                decimal(line.taxRate(), storagePrecision), source(line.sourceDocumentType(), true),
                source(line.sourceDocumentId(), false), source(line.sourceLineId(), false),
                decimal(amounts.net(), false), decimal(amounts.tax(), false), decimal(amounts.gross(), false)));
    }

    static String header(String partner, String warehouse, String currency, LocalDate date, String note, List<String> lines) {
        String canonical = json(Arrays.asList("order-create-v1", partner, warehouse,
                currency.trim().toUpperCase(Locale.ROOT), date == null ? null : date.toString(), note, lines));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String decimal(BigDecimal value, boolean storagePrecision) {
        return (storagePrecision ? value.setScale(4, RoundingMode.HALF_UP) : value).stripTrailingZeros().toPlainString();
    }

    private static String source(String value, boolean uppercase) {
        if (value == null || value.isBlank()) return null;
        return uppercase ? value.trim().toUpperCase(Locale.ROOT) : value.trim();
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
    }
}
