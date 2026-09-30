package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.PlatformApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FulfillmentStatePolicyTest {
    @Test
    void rejectsDraftTerminalAndUnknownStatesForEveryFulfillmentAction() {
        for (String action : new String[]{"RECEIVE", "RESERVE", "SHIP"}) {
            for (String state : new String[]{"DRAFT", "CANCELLED", "CLOSED", "RECEIVED", "FULFILLED", "PENDING_APPROVAL", "UNKNOWN"}) {
                assertThatThrownBy(() -> FulfillmentStatePolicy.requireAllowed(state, action))
                        .isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("DOCUMENT_STATE_CONFLICT");
            }
        }
    }

    @Test
    void allowsPartialAndConfirmedOrdersToContinueFulfillment() {
        for (String state : new String[]{"CONFIRMED", "PARTIALLY_RECEIVED"})
            assertThatCode(() -> FulfillmentStatePolicy.requireAllowed(state, "RECEIVE")).doesNotThrowAnyException();
        for (String state : new String[]{"CONFIRMED", "RESERVED", "PARTIALLY_FULFILLED"}) {
            assertThatCode(() -> FulfillmentStatePolicy.requireAllowed(state, "RESERVE")).doesNotThrowAnyException();
            assertThatCode(() -> FulfillmentStatePolicy.requireAllowed(state, "SHIP")).doesNotThrowAnyException();
        }
    }
}
