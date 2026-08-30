package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public final class InventoryQuantityPolicy {
    private InventoryQuantityPolicy() {
    }

    public static BigDecimal available(BigDecimal onHand, BigDecimal reserved, boolean frozen) {
        if (frozen) return BigDecimal.ZERO;
        return nonNegative(onHand).subtract(nonNegative(reserved)).max(BigDecimal.ZERO);
    }

    public static void requireReservable(
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal requested,
            boolean frozen
    ) {
        requirePositive(requested);
        BigDecimal available = available(onHand, reserved, frozen);
        if (available.compareTo(requested) < 0) {
            throw new PlatformApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "errors.insufficientAvailableStock",
                    java.util.Map.of("available", available, "requested", requested)
            );
        }
    }

    public static void requireShippable(
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal reservationRemaining,
            BigDecimal requested,
            boolean frozen
    ) {
        requirePositive(requested);
        if (frozen || onHand.compareTo(requested) < 0 || reserved.compareTo(requested) < 0
                || reservationRemaining.compareTo(requested) < 0) {
            throw new PlatformApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "errors.insufficientAvailableStock"
            );
        }
    }

    public static BigDecimal inboundAverageCost(
            BigDecimal onHand,
            BigDecimal averageCost,
            BigDecimal incoming,
            BigDecimal incomingCost
    ) {
        requirePositive(incoming);
        BigDecimal next = nonNegative(onHand).add(incoming);
        return nonNegative(onHand).multiply(nonNegative(averageCost))
                .add(incoming.multiply(nonNegative(incomingCost)))
                .divide(next, 8, java.math.RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
