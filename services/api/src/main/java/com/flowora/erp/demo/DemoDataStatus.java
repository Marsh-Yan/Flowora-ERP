package com.flowora.erp.demo;

import java.time.Instant;

public record DemoDataStatus(
        boolean enabled,
        long customers,
        long suppliers,
        long items,
        long warehouses,
        long purchaseOrders,
        long receipts,
        long salesOrders,
        long deliveries,
        long journalEntries,
        long projects,
        Instant lastResetAt,
        long resetCount
) {
}
