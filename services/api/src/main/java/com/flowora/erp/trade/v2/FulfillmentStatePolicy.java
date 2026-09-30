package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.springframework.http.HttpStatus;
import java.util.Set;

final class FulfillmentStatePolicy {
    private FulfillmentStatePolicy() { }

    static void requireAllowed(String state, String operation) {
        Set<String> allowed = switch (operation) {
            case "RECEIVE" -> Set.of("CONFIRMED", "APPROVED", "PARTIALLY_RECEIVED");
            case "RESERVE", "SHIP" -> Set.of("CONFIRMED", "RESERVED", "PARTIALLY_FULFILLED");
            default -> throw new IllegalArgumentException("Unknown fulfillment operation");
        };
        if (!allowed.contains(state)) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "DOCUMENT_STATE_CONFLICT", "errors.documentStateConflict");
        }
    }
}
