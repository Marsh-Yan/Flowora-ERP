package com.flowora.erp.release;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class M3MigrationContractTest {
    private final Path migration = Path.of("src/main/resources/db/migration/V13__trade_advanced_inventory.sql");

    @Test
    void definesInventoryDimensionsMovementsAndConcurrencyGuards() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_inventory_lot",
                "flowora_inventory_serial",
                "flowora_inventory_balance_v2",
                "flowora_stock_movement",
                "flowora_stock_movement_line",
                "flowora_stock_reservation",
                "flowora_stock_freeze",
                "flowora_stock_count",
                "flowora_stock_count_line",
                "uq_flowora_sales_order_request",
                "uq_flowora_purchase_order_request",
                "net_amount",
                "gross_amount",
                "CHECK (on_hand_quantity >= 0 AND reserved_quantity >= 0)",
                "UNIQUE (organization_id, request_id)"
        );
    }

    @Test
    void definesReturnsSourceLinksAndPendingFinanceEvents() throws Exception {
        String sql = Files.readString(migration);

        assertThat(sql).contains(
                "flowora_trade_source_line_link",
                "flowora_sales_return",
                "flowora_purchase_return",
                "flowora_financial_source_event",
                "PENDING_FINANCE",
                "returned_quantity",
                "'inventory:reserve'",
                "'inventory:trace'",
                "'inventory:freeze'",
                "'inventory:return'",
                "'trade:financial-source'"
        );
    }
}
