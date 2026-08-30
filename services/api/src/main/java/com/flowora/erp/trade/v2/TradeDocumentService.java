package com.flowora.erp.trade.v2;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.common.idempotency.IdempotencyService;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.trade.v2.TradeAmountPolicy.Amounts;
import com.flowora.erp.trade.v2.TradeDocumentDtos.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Profile("local")
public class TradeDocumentService {
    private final JdbcTemplate jdbc;
    private final IdempotencyService idempotency;

    public TradeDocumentService(JdbcTemplate jdbc, IdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.idempotency = idempotency;
    }

    @Transactional
    public DocumentView createSalesOrder(FloworaPrincipal actor, SalesOrderRequest request, String requestKey) {
        DocumentView replay = findByRequest(actor.organizationId(), true, requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_SALES_ORDER_CREATE", requestKey);
        requireResources(actor.organizationId(), "flowora_customer", request.customerId(), request.warehouseId(), request.lines());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_sales_order
                    (id,organization_id,number,customer_id,warehouse_id,status,currency_code,order_date,due_date,
                     total_amount,note,sales_user_id,request_id)
                VALUES (?,?,?,?,?,'DRAFT',?,CURRENT_DATE,?,0,?,?,?)
                """, id, actor.organizationId(), number("SO"), request.customerId(), request.warehouseId(),
                currency(request.currencyCode()), request.dueDate(), request.note(), actor.userId(), requireKey(requestKey));
        BigDecimal total = insertSalesLines(actor.organizationId(), id, request.lines());
        jdbc.update("UPDATE flowora_sales_order SET total_amount=? WHERE id=?", total, id);
        return salesOrder(actor.organizationId(), id);
    }

    @Transactional
    public DocumentView createPurchaseOrder(FloworaPrincipal actor, PurchaseOrderRequest request, String requestKey) {
        DocumentView replay = findByRequest(actor.organizationId(), false, requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_PURCHASE_ORDER_CREATE", requestKey);
        requireResources(actor.organizationId(), "flowora_supplier", request.supplierId(), request.warehouseId(), request.lines());
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_purchase_order
                    (id,organization_id,number,supplier_id,warehouse_id,buyer_user_id,status,currency_code,total_amount,
                     order_date,expected_date,note,request_id)
                VALUES (?,?,?,?,?,?,'DRAFT',?,0,CURRENT_DATE,?,?,?)
                """, id, actor.organizationId(), number("PO"), request.supplierId(), request.warehouseId(),
                actor.userId(), currency(request.currencyCode()), request.expectedDate(), request.note(), requireKey(requestKey));
        BigDecimal total = insertPurchaseLines(actor.organizationId(), id, request.lines());
        jdbc.update("UPDATE flowora_purchase_order SET total_amount=? WHERE id=?", total, id);
        return purchaseOrder(actor.organizationId(), id);
    }

    @Transactional(readOnly = true)
    public DocumentView salesOrder(String organizationId, String id) {
        List<DocumentView> rows = jdbc.query("""
                SELECT id,number,customer_id,warehouse_id,status,currency_code,order_date,due_date,total_amount,note,version_no,created_at
                FROM flowora_sales_order WHERE id=? AND organization_id=?
                """, (rs, row) -> new DocumentView(rs.getString("id"), rs.getString("number"), "SALES_ORDER",
                rs.getString("customer_id"), rs.getString("warehouse_id"), rs.getString("status"), rs.getString("currency_code"),
                rs.getDate("order_date").toLocalDate(), localDate(rs.getDate("due_date")), rs.getBigDecimal("total_amount"),
                rs.getString("note"), rs.getLong("version_no"), rs.getTimestamp("created_at").toInstant(),
                salesLines(organizationId, id)), id, organizationId);
        return required(rows, "salesOrder", id);
    }

    @Transactional(readOnly = true)
    public DocumentView purchaseOrder(String organizationId, String id) {
        List<DocumentView> rows = jdbc.query("""
                SELECT id,number,supplier_id,warehouse_id,status,currency_code,order_date,expected_date,total_amount,note,version_no,created_at
                FROM flowora_purchase_order WHERE id=? AND organization_id=?
                """, (rs, row) -> new DocumentView(rs.getString("id"), rs.getString("number"), "PURCHASE_ORDER",
                rs.getString("supplier_id"), rs.getString("warehouse_id"), rs.getString("status"), rs.getString("currency_code"),
                rs.getDate("order_date").toLocalDate(), localDate(rs.getDate("expected_date")), rs.getBigDecimal("total_amount"),
                rs.getString("note"), rs.getLong("version_no"), rs.getTimestamp("created_at").toInstant(),
                purchaseLines(organizationId, id)), id, organizationId);
        return required(rows, "purchaseOrder", id);
    }

    @Transactional
    public DocumentView confirmSalesOrder(String organizationId, String id, long version) {
        int changed = jdbc.update("UPDATE flowora_sales_order SET status='CONFIRMED',version_no=version_no+1 WHERE id=? AND organization_id=? AND status='DRAFT' AND version_no=?", id, organizationId, version);
        if (changed == 0 && !hasStatus("flowora_sales_order", organizationId, id, "CONFIRMED")) stateConflict();
        return salesOrder(organizationId, id);
    }

    @Transactional
    public DocumentView confirmPurchaseOrder(String organizationId, String id, long version) {
        int changed = jdbc.update("UPDATE flowora_purchase_order SET status='CONFIRMED',version_no=version_no+1 WHERE id=? AND organization_id=? AND status='DRAFT' AND version_no=?", id, organizationId, version);
        if (changed == 0 && !hasStatus("flowora_purchase_order", organizationId, id, "CONFIRMED")) stateConflict();
        return purchaseOrder(organizationId, id);
    }

    @Transactional
    public DocumentView cancelSalesOrder(String organizationId, String id, long version) {
        int changed = jdbc.update("""
                UPDATE flowora_sales_order SET status='CANCELLED',version_no=version_no+1
                WHERE id=? AND organization_id=? AND status IN ('DRAFT','CONFIRMED','RESERVED') AND version_no=?
                  AND NOT EXISTS (SELECT 1 FROM flowora_sales_order_line line WHERE line.sales_order_id=? AND line.fulfilled_quantity>0)
                """, id, organizationId, version, id);
        if (changed == 0) stateConflict();
        releaseReservations(organizationId, id);
        jdbc.update("UPDATE flowora_sales_order_line SET cancelled_quantity=ordered_quantity-fulfilled_quantity,version_no=version_no+1 WHERE sales_order_id=? AND organization_id=?", id, organizationId);
        return salesOrder(organizationId, id);
    }

    @Transactional
    public DocumentView cancelPurchaseOrder(String organizationId, String id, long version) {
        int changed = jdbc.update("""
                UPDATE flowora_purchase_order SET status='CANCELLED',version_no=version_no+1
                WHERE id=? AND organization_id=? AND status IN ('DRAFT','CONFIRMED') AND version_no=?
                  AND NOT EXISTS (SELECT 1 FROM flowora_purchase_order_line line WHERE line.purchase_order_id=? AND line.received_quantity>0)
                """, id, organizationId, version, id);
        if (changed == 0) stateConflict();
        return purchaseOrder(organizationId, id);
    }

    private BigDecimal insertSalesLines(String organizationId, String orderId, List<LineRequest> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineRequest line : lines) {
            Amounts amounts = TradeAmountPolicy.calculate(line.quantity(), line.unitPrice(), line.discountRate(), line.taxRate());
            String lineId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO flowora_sales_order_line
                        (id,organization_id,sales_order_id,item_id,ordered_quantity,unit_price,discount_rate,tax_rate,net_amount,tax_amount,gross_amount)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, lineId, organizationId, orderId, line.itemId(), line.quantity(), line.unitPrice(), line.discountRate(),
                    line.taxRate(), amounts.net(), amounts.tax(), amounts.gross());
            linkSource(organizationId, line, "SALES_ORDER", orderId, lineId);
            total = total.add(amounts.gross());
        }
        return total;
    }

    private BigDecimal insertPurchaseLines(String organizationId, String orderId, List<LineRequest> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineRequest line : lines) {
            Amounts amounts = TradeAmountPolicy.calculate(line.quantity(), line.unitPrice(), line.discountRate(), line.taxRate());
            String lineId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO flowora_purchase_order_line
                        (id,organization_id,purchase_order_id,item_id,ordered_quantity,unit_price,discount_rate,tax_rate,net_amount,tax_amount,gross_amount)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, lineId, organizationId, orderId, line.itemId(), line.quantity(), line.unitPrice(), line.discountRate(),
                    line.taxRate(), amounts.net(), amounts.tax(), amounts.gross());
            linkSource(organizationId, line, "PURCHASE_ORDER", orderId, lineId);
            total = total.add(amounts.gross());
        }
        return total;
    }

    private void linkSource(String organizationId, LineRequest line, String targetType, String targetId, String targetLineId) {
        if (blank(line.sourceLineId())) return;
        if (blank(line.sourceDocumentType()) || blank(line.sourceDocumentId())) {
            throw new PlatformApiException(HttpStatus.BAD_REQUEST, "REFERENCE_CONFLICT", "errors.referenceConflict");
        }
        jdbc.update("""
                INSERT INTO flowora_trade_source_line_link
                    (id,organization_id,source_document_type,source_document_id,source_line_id,target_document_type,target_document_id,target_line_id,linked_quantity)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), organizationId, line.sourceDocumentType().trim(), line.sourceDocumentId().trim(),
                line.sourceLineId().trim(), targetType, targetId, targetLineId, line.quantity());
    }

    private List<LineView> salesLines(String organizationId, String orderId) {
        return jdbc.query("""
                SELECT id,item_id,ordered_quantity,fulfilled_quantity,reserved_quantity,returned_quantity,unit_price,
                       discount_rate,tax_rate,net_amount,tax_amount,gross_amount
                FROM flowora_sales_order_line WHERE organization_id=? AND sales_order_id=? ORDER BY created_at,id
                """, (rs, row) -> new LineView(rs.getString("id"), rs.getString("item_id"), rs.getBigDecimal("ordered_quantity"),
                rs.getBigDecimal("fulfilled_quantity"), rs.getBigDecimal("reserved_quantity"), rs.getBigDecimal("returned_quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_rate"), rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("gross_amount")), organizationId, orderId);
    }

    private List<LineView> purchaseLines(String organizationId, String orderId) {
        return jdbc.query("""
                SELECT id,item_id,ordered_quantity,received_quantity,returned_quantity,unit_price,discount_rate,tax_rate,
                       net_amount,tax_amount,gross_amount
                FROM flowora_purchase_order_line WHERE organization_id=? AND purchase_order_id=? ORDER BY created_at,id
                """, (rs, row) -> new LineView(rs.getString("id"), rs.getString("item_id"), rs.getBigDecimal("ordered_quantity"),
                rs.getBigDecimal("received_quantity"), BigDecimal.ZERO, rs.getBigDecimal("returned_quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_rate"), rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("net_amount"), rs.getBigDecimal("tax_amount"), rs.getBigDecimal("gross_amount")), organizationId, orderId);
    }

    private void releaseReservations(String organizationId, String orderId) {
        List<ReservationRelease> reservations = jdbc.query("""
                SELECT id,warehouse_id,location_id,item_id,lot_id,serial_id,reserved_quantity-consumed_quantity remaining
                FROM flowora_stock_reservation WHERE organization_id=? AND sales_order_id=? AND status IN ('ACTIVE','PARTIAL') FOR UPDATE
                """, (rs, row) -> new ReservationRelease(rs.getString("id"), rs.getString("warehouse_id"), rs.getString("location_id"),
                rs.getString("item_id"), rs.getString("lot_id"), rs.getString("serial_id"), rs.getBigDecimal("remaining")), organizationId, orderId);
        for (ReservationRelease reservation : reservations) {
            jdbc.update("""
                    UPDATE flowora_inventory_balance_v2 SET reserved_quantity=reserved_quantity-?,version_no=version_no+1
                    WHERE organization_id=? AND warehouse_id=? AND location_id=? AND item_id=? AND lot_id=? AND serial_id=?
                    """, reservation.remaining(), organizationId, reservation.warehouseId(), reservation.locationId(), reservation.itemId(),
                    reservation.lotId(), reservation.serialId());
            if (!blank(reservation.serialId())) jdbc.update("UPDATE flowora_inventory_serial SET status='AVAILABLE',version_no=version_no+1 WHERE id=? AND organization_id=?", reservation.serialId(), organizationId);
            jdbc.update("UPDATE flowora_stock_reservation SET status='RELEASED',version_no=version_no+1 WHERE id=?", reservation.id());
        }
    }

    private void requireResources(String organizationId, String partnerTable, String partnerId, String warehouseId, List<LineRequest> lines) {
        require(partnerTable, organizationId, partnerId);
        require("flowora_warehouse", organizationId, warehouseId);
        for (LineRequest line : lines) require("flowora_item", organizationId, line.itemId());
    }

    private void require(String table, String organizationId, String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=? AND organization_id=?", Integer.class, id, organizationId);
        if (count == null || count == 0) throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound");
    }

    private DocumentView findByRequest(String organizationId, boolean sales, String requestKey) {
        if (blank(requestKey)) return null;
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        List<String> ids = jdbc.query("SELECT id FROM " + table + " WHERE organization_id=? AND request_id=?", (rs, row) -> rs.getString(1), organizationId, requestKey.trim());
        return ids.isEmpty() ? null : sales ? salesOrder(organizationId, ids.getFirst()) : purchaseOrder(organizationId, ids.getFirst());
    }

    private boolean hasStatus(String table, String organizationId, String id, String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=? AND organization_id=? AND status=?", Integer.class, id, organizationId, status);
        return count != null && count > 0;
    }

    private void claim(String organizationId, String operation, String requestKey) {
        if (!idempotency.claim(organizationId, operation, requireKey(requestKey))) {
            throw new PlatformApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "errors.idempotencyConflict");
        }
    }

    private static <T> T required(List<T> rows, String type, String id) {
        if (rows.isEmpty()) throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound", java.util.Map.of("resourceType", type, "resourceId", id));
        return rows.getFirst();
    }

    private static void stateConflict() {
        throw new PlatformApiException(HttpStatus.CONFLICT, "DOCUMENT_STATE_CONFLICT", "errors.documentStateConflict");
    }

    private static String requireKey(String value) {
        if (blank(value)) throw new PlatformApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "errors.idempotencyKeyRequired");
        return value.trim();
    }

    private static String currency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static java.time.LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static String number(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private record ReservationRelease(String id, String warehouseId, String locationId, String itemId, String lotId,
                                      String serialId, BigDecimal remaining) {
    }
}
