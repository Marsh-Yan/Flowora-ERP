package com.flowora.erp.inventory;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.inventory.InventoryDtos.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local | production")
public class CanonicalInventoryReader {
    private final JdbcTemplate jdbc;
    public CanonicalInventoryReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public StockSummaryResponse summary(String organizationId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(inventory_value),0) inventory_value, COUNT(*) balance_count,
                  (SELECT COUNT(*) FROM flowora_inventory_delta_v2 WHERE organization_id=?) ledger_count
                FROM flowora_inventory_summary_v2 WHERE organization_id=?
                """, (rs, index) -> new StockSummaryResponse(rs.getBigDecimal("inventory_value"),
                rs.getLong("balance_count"), rs.getLong("ledger_count")), organizationId, organizationId);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockBalanceResponse> balances(String organizationId, String warehouseId, Pageable page) {
        String where = " WHERE organization_id=? AND (?='' OR warehouse_id=?)";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_inventory_summary_v2" + where,
                Long.class, organizationId, warehouseId, warehouseId);
        var rows = jdbc.query("SELECT * FROM flowora_inventory_summary_v2" + where + " ORDER BY warehouse_id,item_id LIMIT ? OFFSET ?",
                (rs, index) -> new StockBalanceResponse(rs.getString("id"), rs.getString("warehouse_id"), rs.getString("item_id"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("average_cost"), rs.getBigDecimal("inventory_value")),
                organizationId, warehouseId, warehouseId, page.getPageSize(), page.getOffset());
        return PageResponse.from(new PageImpl<>(rows, page, count == null ? 0 : count));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockLedgerResponse> ledger(String organizationId, String warehouseId, String itemId, Pageable page) {
        String where = " WHERE organization_id=? AND (?='' OR warehouse_id=?) AND (?='' OR item_id=?)";
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_inventory_ledger_v2" + where,
                Long.class, organizationId, warehouseId, warehouseId, itemId, itemId);
        var rows = jdbc.query("SELECT * FROM flowora_inventory_ledger_v2" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs, index) -> new StockLedgerResponse(rs.getString("id"), rs.getString("warehouse_id"), rs.getString("item_id"),
                        movementType(rs.getString("movement_type"), rs.getBigDecimal("quantity_delta").signum()),
                        rs.getString("document_type"), rs.getString("document_id"), rs.getBigDecimal("quantity_delta"),
                        rs.getBigDecimal("unit_cost"), rs.getBigDecimal("value_delta"), rs.getBigDecimal("balance_quantity"),
                        rs.getBigDecimal("balance_value"), rs.getString("actor_user_id"), rs.getTimestamp("created_at").toInstant()),
                organizationId, warehouseId, warehouseId, itemId, itemId, page.getPageSize(), page.getOffset());
        return PageResponse.from(new PageImpl<>(rows, page, count == null ? 0 : count));
    }

    private static InventoryMovementType movementType(String type, int sign) {
        return "TRANSFER".equals(type) ? (sign > 0 ? InventoryMovementType.TRANSFER_IN : InventoryMovementType.TRANSFER_OUT)
                : InventoryMovementType.valueOf(type);
    }
}
