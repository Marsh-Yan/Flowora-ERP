package com.flowora.erp.trade.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.common.idempotency.IdempotencyService;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.inventory.StockBalanceEntity;
import com.flowora.erp.trade.v2.TradeInventoryDtos.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Profile("local | production")
public class TradeInventoryService {
    private final JdbcTemplate jdbc;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public TradeInventoryService(JdbcTemplate jdbc, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void lockCompatibilityOrder(String organizationId, boolean sales, String id) {
        lockFulfillableOrder(organizationId, sales, id, sales ? "SHIP" : "RECEIVE");
    }

    @Transactional
    public StockBalanceEntity compatibilityBalance(String organizationId, String warehouseId, String itemId) {
        requireUntracked(organizationId, itemId);
        Balance balance = lockedBalance(organizationId, warehouseId, null, itemId, null, null);
        return new StockBalanceEntity(organizationId, warehouseId, itemId, balance.onHand(), balance.averageCost());
    }

    // Keep compatibility document/approval/accounting contracts, but use the same
    // dimension locks and movement ledger as native v2 operations.
    @Transactional
    public BigDecimal postCompatibilityDelta(String organizationId, String warehouseId, String itemId,
            BigDecimal delta, BigDecimal unitCost, String movementType, String sourceType, String sourceId, String actorId) {
        requireUntracked(organizationId, itemId);
        String requestKey = "compat:" + movementType + ":" + sourceId;
        if (replay(organizationId, requestKey) != null) return null;
        Balance balance = lockedBalance(organizationId, warehouseId, null, itemId, null, null);
        if (balance.frozen()) conflict("STOCK_FROZEN", "errors.stockFrozen");
        if (delta.signum() == 0) return BigDecimal.ZERO;
        BigDecimal appliedCost = delta.signum() > 0 ? unitCost : balance.averageCost();
        if (delta.signum() > 0) {
            inbound(organizationId, warehouseId, null, itemId, null, null, delta, unitCost);
        } else {
            InventoryQuantityPolicy.requireReservable(balance.onHand(), balance.reserved(), delta.abs(), balance.frozen());
            jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=on_hand_quantity+?,version_no=version_no+1 WHERE id=?",delta,balance.id());
        }
        FloworaPrincipal sourceActor = new FloworaPrincipal(actorId,actorId,actorId,organizationId,organizationId,List.of());
        String id = createMovement(sourceActor, movementType, sourceType, sourceId, null, requestKey);
        insertMovementLine(organizationId,id,1,itemId,delta.signum()<0?warehouseId:null,null,
                delta.signum()>0?warehouseId:null,null,null,null,delta.abs(),appliedCost,sourceType,sourceId);
        return appliedCost;
    }

    private void requireUntracked(String organizationId, String itemId) {
        if (!"NONE".equals(tracking(organizationId,itemId))) conflict("TRACKING_REQUIRED", "errors.trackingRequired");
    }

    @Transactional
    public void linkCompatibilityMovement(String organizationId, String sourceType, String sourceId, String lineId) {
        jdbc.update("""
                UPDATE flowora_stock_movement_line line JOIN flowora_stock_movement movement ON movement.id=line.movement_id
                SET line.source_line_type=?,line.source_line_id=?
                WHERE movement.organization_id=? AND movement.source_type=? AND movement.source_id=?
                """, sourceType + "_LINE",lineId,organizationId,sourceType,sourceId);
    }

    @Transactional(readOnly = true)
    public boolean hasOpenOrderLines(String organizationId, boolean sales, String orderId) {
        String sql = sales
                ? "SELECT COUNT(*) FROM flowora_sales_order_line WHERE organization_id=? AND sales_order_id=? AND fulfilled_quantity<ordered_quantity-cancelled_quantity"
                : "SELECT COUNT(*) FROM flowora_purchase_order_line WHERE organization_id=? AND purchase_order_id=? AND received_quantity<ordered_quantity";
        return jdbc.queryForObject(sql,Integer.class,organizationId,orderId)>0;
    }

    @Transactional
    public void requireCompatibilitySalesQuantity(String organizationId, String orderId, String lineId, BigDecimal quantity) {
        requireOrderReservable(organizationId, orderId, lineId, quantity);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityView> availability(String organizationId, String warehouseId, String itemId) {
        return jdbc.query("""
                SELECT warehouse_id, location_id, item_id, lot_id, serial_id, on_hand_quantity,
                       reserved_quantity, CASE WHEN frozen THEN 0 ELSE GREATEST(on_hand_quantity-reserved_quantity, 0) END available_quantity,
                       average_cost, frozen
                FROM flowora_inventory_balance_v2
                WHERE organization_id = ? AND (? = '' OR warehouse_id = ?) AND (? = '' OR item_id = ?)
                ORDER BY warehouse_id, location_id, item_id, lot_id, serial_id
                """, (rs, row) -> new AvailabilityView(
                rs.getString("warehouse_id"), blankToNull(rs.getString("location_id")), rs.getString("item_id"),
                blankToNull(rs.getString("lot_id")), blankToNull(rs.getString("serial_id")),
                rs.getBigDecimal("on_hand_quantity"), rs.getBigDecimal("reserved_quantity"),
                rs.getBigDecimal("available_quantity"), rs.getBigDecimal("average_cost"), rs.getBoolean("frozen")
        ), organizationId, clean(warehouseId), clean(warehouseId), clean(itemId), clean(itemId));
    }

    @Transactional
    public MovementView receive(FloworaPrincipal actor, ReceiptRequest request, String requestKey) {
        MovementView replay = replay(actor.organizationId(), requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_RECEIPT", requestKey);
        lockFulfillableOrder(actor.organizationId(), false, request.purchaseOrderId(), "RECEIVE");
        requireOrderWarehouse(actor.organizationId(), false, request.purchaseOrderId(), request.warehouseId());
        String receiptId = UUID.randomUUID().toString();
        String movementId = createMovement(actor, "RECEIPT", "PURCHASE_RECEIPT", receiptId, null, requestKey);
        jdbc.update("""
                INSERT INTO flowora_purchase_receipt
                    (id, organization_id, number, purchase_order_id, warehouse_id, received_by, status)
                VALUES (?, ?, ?, ?, ?, ?, 'POSTED')
                """, receiptId, actor.organizationId(), number("GR"), request.purchaseOrderId(),
                request.warehouseId(), actor.userId());
        int sequence = 1;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReceiptLineRequest line : request.lines()) {
            requireOrderLine(actor.organizationId(), request.purchaseOrderId(), line.purchaseOrderLineId(), line.itemId());
            BigDecimal accepted = line.quantity().subtract(line.rejectedQuantity());
            if (accepted.signum() < 0) invalid("Rejected quantity exceeds received quantity");
            String tracking = tracking(actor.organizationId(), line.itemId());
            String lotId = resolveLot(actor.organizationId(), line, tracking);
            List<String> serialIds = resolveSerials(actor.organizationId(), request.warehouseId(), line, lotId, tracking, accepted);
            if ("SERIAL".equals(tracking)) {
                for (String serialId : serialIds) {
                    String receiptLineId = insertReceiptLine(actor.organizationId(), receiptId, line, lotId, serialId, BigDecimal.ONE, BigDecimal.ZERO);
                    inbound(actor.organizationId(), request.warehouseId(), line.locationId(), line.itemId(), lotId, serialId, BigDecimal.ONE, line.unitCost());
                    insertMovementLine(actor.organizationId(), movementId, sequence++, line.itemId(), null, null,
                            request.warehouseId(), line.locationId(), lotId, serialId, BigDecimal.ONE, line.unitCost(),
                            "PURCHASE_RECEIPT_LINE", receiptLineId);
                }
                if (line.rejectedQuantity().signum() > 0) insertReceiptLine(actor.organizationId(), receiptId, line, lotId, null, BigDecimal.ZERO, line.rejectedQuantity());
            } else {
                String receiptLineId = insertReceiptLine(actor.organizationId(), receiptId, line, lotId, null, accepted, line.rejectedQuantity());
                if (accepted.signum() > 0) {
                    inbound(actor.organizationId(), request.warehouseId(), line.locationId(), line.itemId(), lotId, null, accepted, line.unitCost());
                    insertMovementLine(actor.organizationId(), movementId, sequence++, line.itemId(), null, null,
                            request.warehouseId(), line.locationId(), lotId, null, accepted, line.unitCost(),
                            "PURCHASE_RECEIPT_LINE", receiptLineId);
                }
            }
            int changed = jdbc.update("""
                    UPDATE flowora_purchase_order_line
                    SET received_quantity=received_quantity+?, rejected_quantity=rejected_quantity+?, version_no=version_no+1
                    WHERE id=? AND organization_id=? AND purchase_order_id=?
                      AND received_quantity+? <= ordered_quantity
                    """, accepted, line.rejectedQuantity(), line.purchaseOrderLineId(), actor.organizationId(),
                    request.purchaseOrderId(), accepted);
            if (changed == 0) conflict("RECEIPT_QUANTITY_EXCEEDED", "errors.receiptQuantityExceeded");
            totalQuantity = totalQuantity.add(accepted);
            totalAmount = totalAmount.add(accepted.multiply(line.unitCost()));
        }
        updatePurchaseOrderStatus(actor.organizationId(), request.purchaseOrderId());
        createFinancialEvent(actor.organizationId(), "PURCHASE_RECEIPT_SOURCE", "PURCHASE_RECEIPT", receiptId,
                orderCurrency(actor.organizationId(), false, request.purchaseOrderId()), totalQuantity, totalAmount,
                Map.of("purchaseOrderId", request.purchaseOrderId(), "movementId", movementId));
        return movement(actor.organizationId(), movementId);
    }

    @Transactional
    public List<ReservationView> reserve(FloworaPrincipal actor, ReservationRequest request) {
        lockFulfillableOrder(actor.organizationId(), true, request.salesOrderId(), "RESERVE");
        List<ReservationView> result = new ArrayList<>();
        for (ReservationLineRequest line : request.lines()) {
            requireOrderWarehouse(actor.organizationId(), true, request.salesOrderId(), line.warehouseId());
            requireSalesOrderLine(actor.organizationId(), request.salesOrderId(), line.salesOrderLineId(), line.itemId());
            requireOrderReservable(actor.organizationId(), request.salesOrderId(), line.salesOrderLineId(), line.quantity());
            Balance balance = lockedBalance(actor.organizationId(), line.warehouseId(), line.locationId(), line.itemId(), line.lotId(), line.serialId());
            InventoryQuantityPolicy.requireReservable(balance.onHand(), balance.reserved(), line.quantity(), balance.frozen());
            if (!clean(line.serialId()).isEmpty()) requireSerialStatus(actor.organizationId(), line.serialId(), "AVAILABLE");
            String id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO flowora_stock_reservation
                        (id, organization_id, sales_order_id, sales_order_line_id, warehouse_id, location_id,
                         item_id, lot_id, serial_id, reserved_quantity, expires_at, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, actor.organizationId(), request.salesOrderId(), line.salesOrderLineId(),
                    line.warehouseId(), key(line.locationId()), line.itemId(), key(line.lotId()), key(line.serialId()),
                    line.quantity(), timestamp(request.expiresAt()), actor.userId());
            jdbc.update("UPDATE flowora_inventory_balance_v2 SET reserved_quantity=reserved_quantity+?, version_no=version_no+1 WHERE id=?", line.quantity(), balance.id());
            jdbc.update("UPDATE flowora_sales_order_line SET reserved_quantity=reserved_quantity+?, version_no=version_no+1 WHERE id=?", line.quantity(), line.salesOrderLineId());
            if (!clean(line.serialId()).isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET status='RESERVED', version_no=version_no+1 WHERE id=?", line.serialId());
            result.add(reservation(actor.organizationId(), id));
        }
        jdbc.update("UPDATE flowora_sales_order SET status=CASE WHEN status='PARTIALLY_FULFILLED' THEN status ELSE 'RESERVED' END, version_no=version_no+1 WHERE id=? AND organization_id=?", request.salesOrderId(), actor.organizationId());
        return result;
    }
    @Transactional
    public MovementView ship(FloworaPrincipal actor, ShipmentRequest request, String requestKey) {
        MovementView replay = replay(actor.organizationId(), requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_SHIPMENT", requestKey);
        lockFulfillableOrder(actor.organizationId(), true, request.salesOrderId(), "SHIP");
        Reservation first = lockedReservation(actor.organizationId(), request.lines().getFirst().reservationId());
        String deliveryId = UUID.randomUUID().toString();
        String movementId = createMovement(actor, "SHIPMENT", "SALES_DELIVERY", deliveryId, null, requestKey);
        jdbc.update("""
                INSERT INTO flowora_sales_delivery
                    (id, organization_id, number, sales_order_id, warehouse_id, status, actor_user_id)
                VALUES (?, ?, ?, ?, ?, 'POSTED', ?)
                """, deliveryId, actor.organizationId(), number("DO"), request.salesOrderId(), first.warehouseId(), actor.userId());
        int sequence = 1;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (ShipmentLineRequest line : request.lines()) {
            Reservation reservation = lockedReservation(actor.organizationId(), line.reservationId());
            if (!reservation.salesOrderId().equals(request.salesOrderId())
                    || !reservation.salesOrderLineId().equals(line.salesOrderLineId())) invalid("Reservation source mismatch");
            Balance balance = lockedBalance(actor.organizationId(), reservation.warehouseId(), reservation.locationId(),
                    reservation.itemId(), reservation.lotId(), reservation.serialId());
            BigDecimal remaining = reservation.reserved().subtract(reservation.consumed());
            InventoryQuantityPolicy.requireShippable(balance.onHand(), balance.reserved(), remaining, line.quantity(), balance.frozen());
            jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=on_hand_quantity-?, reserved_quantity=reserved_quantity-?, version_no=version_no+1 WHERE id=?",
                    line.quantity(), line.quantity(), balance.id());
            String reservationStatus = remaining.compareTo(line.quantity()) == 0 ? "CONSUMED" : "PARTIAL";
            jdbc.update("UPDATE flowora_stock_reservation SET consumed_quantity=consumed_quantity+?, status=?, version_no=version_no+1 WHERE id=?",
                    line.quantity(), reservationStatus, reservation.id());
            int changed = jdbc.update("""
                    UPDATE flowora_sales_order_line
                    SET fulfilled_quantity=fulfilled_quantity+?, reserved_quantity=reserved_quantity-?, version_no=version_no+1
                    WHERE id=? AND organization_id=? AND fulfilled_quantity+? <= ordered_quantity-cancelled_quantity
                    """, line.quantity(), line.quantity(), line.salesOrderLineId(), actor.organizationId(), line.quantity());
            if (changed == 0) conflict("FULFILLMENT_QUANTITY_EXCEEDED", "errors.fulfillmentQuantityExceeded");
            String deliveryLineId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO flowora_sales_delivery_line
                        (id, organization_id, delivery_id, sales_order_line_id, item_id, location_id, lot_id, serial_id, quantity, unit_cost)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, deliveryLineId, actor.organizationId(), deliveryId, line.salesOrderLineId(), reservation.itemId(),
                    nullable(reservation.locationId()), nullable(reservation.lotId()), nullable(reservation.serialId()), line.quantity(), balance.averageCost());
            insertMovementLine(actor.organizationId(), movementId, sequence++, reservation.itemId(), reservation.warehouseId(),
                    reservation.locationId(), null, null, reservation.lotId(), reservation.serialId(), line.quantity(),
                    balance.averageCost(), "SALES_DELIVERY_LINE", deliveryLineId);
            if (!reservation.serialId().isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET status='SHIPPED', warehouse_id=NULL, location_id=NULL, version_no=version_no+1 WHERE id=?", reservation.serialId());
            totalQuantity = totalQuantity.add(line.quantity());
            totalValue = totalValue.add(line.quantity().multiply(balance.averageCost()));
        }
        updateSalesOrderStatus(actor.organizationId(), request.salesOrderId());
        createFinancialEvent(actor.organizationId(), "SALES_DELIVERY_COGS_SOURCE", "SALES_DELIVERY", deliveryId,
                orderCurrency(actor.organizationId(), true, request.salesOrderId()), totalQuantity, totalValue,
                Map.of("salesOrderId", request.salesOrderId(), "movementId", movementId));
        return movement(actor.organizationId(), movementId);
    }

    @Transactional
    public MovementView transfer(FloworaPrincipal actor, TransferRequest request, String requestKey) {
        if (request.sourceWarehouseId().equals(request.targetWarehouseId())) invalid("Warehouses must differ");
        MovementView replay = replay(actor.organizationId(), requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_TRANSFER", requestKey);
        String transferId = UUID.randomUUID().toString();
        String movementId = createMovement(actor, "TRANSFER", "STOCK_TRANSFER", transferId, null, requestKey);
        jdbc.update("INSERT INTO flowora_stock_transfer (id, organization_id, number, source_warehouse_id, target_warehouse_id, actor_user_id, status) VALUES (?, ?, ?, ?, ?, ?, 'POSTED')",
                transferId, actor.organizationId(), number("TR"), request.sourceWarehouseId(), request.targetWarehouseId(), actor.userId());
        int sequence = 1;
        for (TransferLineRequest line : request.lines()) {
            Balance source = lockedBalance(actor.organizationId(), request.sourceWarehouseId(), line.sourceLocationId(), line.itemId(), line.lotId(), line.serialId());
            InventoryQuantityPolicy.requireReservable(source.onHand(), source.reserved(), line.quantity(), source.frozen());
            jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=on_hand_quantity-?, version_no=version_no+1 WHERE id=?", line.quantity(), source.id());
            inbound(actor.organizationId(), request.targetWarehouseId(), line.targetLocationId(), line.itemId(), line.lotId(), line.serialId(), line.quantity(), source.averageCost());
            jdbc.update("INSERT INTO flowora_stock_transfer_line (id, organization_id, stock_transfer_id, item_id, quantity, unit_cost) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), actor.organizationId(), transferId, line.itemId(), line.quantity(), source.averageCost());
            insertMovementLine(actor.organizationId(), movementId, sequence++, line.itemId(), request.sourceWarehouseId(), line.sourceLocationId(),
                    request.targetWarehouseId(), line.targetLocationId(), line.lotId(), line.serialId(), line.quantity(), source.averageCost(), "STOCK_TRANSFER", transferId);
            if (!clean(line.serialId()).isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET warehouse_id=?, location_id=?, version_no=version_no+1 WHERE id=?",
                    request.targetWarehouseId(), nullable(line.targetLocationId()), line.serialId());
        }
        return movement(actor.organizationId(), movementId);
    }
    @Transactional
    public ReservationView releaseReservation(FloworaPrincipal actor, String reservationId) {
        List<String> orders = jdbc.query("SELECT sales_order_id FROM flowora_stock_reservation WHERE id=? AND organization_id=?",
                (rs, row) -> rs.getString(1), reservationId, actor.organizationId());
        if (orders.isEmpty()) notFound("stockReservation", reservationId);
        jdbc.queryForObject("SELECT version_no FROM flowora_sales_order WHERE id=? AND organization_id=? FOR UPDATE",
                Long.class, orders.getFirst(), actor.organizationId());
        Reservation reservation = lockedReservation(actor.organizationId(), reservationId);
        BigDecimal remaining = reservation.reserved().subtract(reservation.consumed());
        Balance balance = lockedBalance(actor.organizationId(), reservation.warehouseId(), reservation.locationId(),
                reservation.itemId(), reservation.lotId(), reservation.serialId());
        if (balance.reserved().compareTo(remaining) < 0) conflict("RESERVATION_STATE_CONFLICT", "errors.reservationStateConflict");
        jdbc.update("UPDATE flowora_inventory_balance_v2 SET reserved_quantity=reserved_quantity-?,version_no=version_no+1 WHERE id=?", remaining, balance.id());
        jdbc.update("UPDATE flowora_sales_order_line SET reserved_quantity=reserved_quantity-?,version_no=version_no+1 WHERE id=? AND organization_id=?", remaining, reservation.salesOrderLineId(), actor.organizationId());
        jdbc.update("UPDATE flowora_stock_reservation SET status='RELEASED',version_no=version_no+1 WHERE id=?", reservation.id());
        if (!clean(reservation.serialId()).isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET status='AVAILABLE',version_no=version_no+1 WHERE id=?", reservation.serialId());
        return reservation(actor.organizationId(), reservation.id());
    }

    @Transactional
    public Map<String, Integer> expireReservations(FloworaPrincipal actor) {
        List<String> ids = jdbc.query("""
                SELECT id FROM flowora_stock_reservation
                WHERE organization_id=? AND status IN ('ACTIVE','PARTIAL') AND expires_at IS NOT NULL AND expires_at<=CURRENT_TIMESTAMP
                ORDER BY expires_at
                """, (rs, row) -> rs.getString(1), actor.organizationId());
        ids.forEach(id -> releaseReservation(actor, id));
        return Map.of("released", ids.size());
    }

    @Transactional
    public MovementView count(FloworaPrincipal actor, CountRequest request, String requestKey) {
        MovementView replay = replay(actor.organizationId(), requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), "M3_STOCK_COUNT", requestKey);
        requireResource("flowora_warehouse", request.warehouseId(), actor.organizationId());
        String countId = UUID.randomUUID().toString();
        String movementId = createMovement(actor, "COUNT", "STOCK_COUNT", countId, null, requestKey);
        jdbc.update("INSERT INTO flowora_stock_count (id,organization_id,number,warehouse_id,location_id,counted_by,status,actor_user_id,request_id) VALUES (?,?,?,?,?,?,'POSTED',?,?)",
                countId, actor.organizationId(), number("CNT"), request.warehouseId(), key(request.locationId()), actor.userId(), actor.userId(), requireKey(requestKey));
        int sequence = 1;
        BigDecimal differenceValue = BigDecimal.ZERO;
        for (CountLineRequest line : request.lines()) {
            requireResource("flowora_item", line.itemId(), actor.organizationId());
            Balance balance = lockedBalance(actor.organizationId(), request.warehouseId(), request.locationId(), line.itemId(), line.lotId(), line.serialId());
            if (line.countedQuantity().compareTo(balance.reserved()) < 0) conflict("COUNT_BELOW_RESERVED", "errors.countBelowReserved");
            if (!clean(line.serialId()).isEmpty() && line.countedQuantity().compareTo(BigDecimal.ONE) > 0) invalid("Serial count must be zero or one");
            BigDecimal difference = line.countedQuantity().subtract(balance.onHand());
            jdbc.update("""
                    INSERT INTO flowora_stock_count_line
                        (id,organization_id,stock_count_id,item_id,lot_id,serial_id,expected_quantity,counted_quantity,difference_quantity,unit_cost)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID().toString(), actor.organizationId(), countId, line.itemId(), key(line.lotId()), key(line.serialId()),
                    balance.onHand(), line.countedQuantity(), difference, balance.averageCost());
            jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=?,version_no=version_no+1 WHERE id=?", line.countedQuantity(), balance.id());
            if (difference.signum() != 0) {
                insertMovementLine(actor.organizationId(), movementId, sequence++, line.itemId(),
                        difference.signum() < 0 ? request.warehouseId() : null, difference.signum() < 0 ? request.locationId() : null,
                        difference.signum() > 0 ? request.warehouseId() : null, difference.signum() > 0 ? request.locationId() : null,
                        line.lotId(), line.serialId(), difference.abs(), balance.averageCost(), "STOCK_COUNT_LINE", countId);
                differenceValue = differenceValue.add(difference.multiply(balance.averageCost()));
            }
            if (!clean(line.serialId()).isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET status=?,warehouse_id=?,location_id=?,version_no=version_no+1 WHERE id=? AND organization_id=?",
                    line.countedQuantity().signum() == 0 ? "MISSING" : "AVAILABLE", line.countedQuantity().signum() == 0 ? null : request.warehouseId(),
                    line.countedQuantity().signum() == 0 ? null : nullable(request.locationId()), line.serialId(), actor.organizationId());
        }
        createFinancialEvent(actor.organizationId(), "INVENTORY_COUNT_SOURCE", "STOCK_COUNT", countId,
                baseCurrency(actor.organizationId()), BigDecimal.valueOf(request.lines().size()), differenceValue, Map.of("movementId", movementId));
        return movement(actor.organizationId(), movementId);
    }


    @Transactional
    public Map<String, String> freeze(FloworaPrincipal actor, FreezeRequest request) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_stock_freeze
                    (id, organization_id, warehouse_id, location_id, item_id, lot_id, reason, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, actor.organizationId(), request.warehouseId(), key(request.locationId()), nullable(request.itemId()),
                nullable(request.lotId()), request.reason().trim(), actor.userId());
        jdbc.update("""
                UPDATE flowora_inventory_balance_v2 SET frozen=TRUE, version_no=version_no+1
                WHERE organization_id=? AND warehouse_id=? AND (?='' OR location_id=?)
                  AND (? IS NULL OR item_id=?) AND (? IS NULL OR lot_id=?)
                """, actor.organizationId(), request.warehouseId(), key(request.locationId()), key(request.locationId()),
                nullable(request.itemId()), nullable(request.itemId()), nullable(request.lotId()), nullable(request.lotId()));
        return Map.of("id", id, "status", "ACTIVE");
    }

    @Transactional
    public Map<String, String> releaseFreeze(FloworaPrincipal actor, String freezeId) {
        int changed = jdbc.update("UPDATE flowora_stock_freeze SET status='RELEASED', released_by=?, released_at=CURRENT_TIMESTAMP WHERE id=? AND organization_id=? AND status='ACTIVE'",
                actor.userId(), freezeId, actor.organizationId());
        if (changed == 0) notFound("stockFreeze", freezeId);
        jdbc.update("""
                UPDATE flowora_inventory_balance_v2 balance
                JOIN flowora_stock_freeze freeze_record ON freeze_record.id=? AND freeze_record.organization_id=balance.organization_id
                SET balance.frozen=FALSE, balance.version_no=balance.version_no+1
                WHERE balance.organization_id=? AND balance.warehouse_id=freeze_record.warehouse_id
                  AND (freeze_record.location_id='' OR balance.location_id=freeze_record.location_id)
                  AND (freeze_record.item_id IS NULL OR balance.item_id=freeze_record.item_id)
                  AND (freeze_record.lot_id IS NULL OR balance.lot_id=freeze_record.lot_id)
                  AND NOT EXISTS (
                      SELECT 1 FROM flowora_stock_freeze active_freeze
                      WHERE active_freeze.organization_id=balance.organization_id
                        AND active_freeze.status='ACTIVE'
                        AND active_freeze.warehouse_id=balance.warehouse_id
                        AND (active_freeze.location_id='' OR active_freeze.location_id=balance.location_id)
                        AND (active_freeze.item_id IS NULL OR active_freeze.item_id=balance.item_id)
                        AND (active_freeze.lot_id IS NULL OR active_freeze.lot_id=balance.lot_id)
                  )
                """, freezeId, actor.organizationId());
        return Map.of("id", freezeId, "status", "RELEASED");
    }

    @Transactional(readOnly = true)
    public TraceView trace(String organizationId, String itemId, String lotId, String serialId) {
        List<String> ids = jdbc.query("""
                SELECT DISTINCT movement_id FROM flowora_stock_movement_line
                WHERE organization_id=? AND (?='' OR item_id=?) AND (?='' OR lot_id=?) AND (?='' OR serial_id=?)
                ORDER BY movement_id
                """, (rs, row) -> rs.getString(1), organizationId, clean(itemId), clean(itemId),
                clean(lotId), clean(lotId), clean(serialId), clean(serialId));
        return new TraceView(clean(itemId), blankToNull(clean(lotId)), blankToNull(clean(serialId)),
                ids.stream().map(id -> movement(organizationId, id)).toList());
    }

    @Transactional(readOnly = true)
    public List<FinancialSourceEventView> financialEvents(String organizationId, String status) {
        return jdbc.query("""
                SELECT id,event_type,source_type,source_id,currency_code,quantity,amount,status,payload_json,occurred_at
                FROM flowora_financial_source_event WHERE organization_id=? AND (?='' OR status=?) ORDER BY occurred_at DESC
                """, (rs, row) -> new FinancialSourceEventView(
                rs.getString("id"), rs.getString("event_type"), rs.getString("source_type"), rs.getString("source_id"),
                rs.getString("currency_code"), rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"), rs.getString("status"),
                readMap(rs.getString("payload_json")), rs.getTimestamp("occurred_at").toInstant()
        ), organizationId, clean(status), clean(status));
    }
    @Transactional
    public MovementView salesReturn(FloworaPrincipal actor, ReturnRequest request, String requestKey) {
        return reverse(actor, request, requestKey, true);
    }

    @Transactional
    public MovementView purchaseReturn(FloworaPrincipal actor, ReturnRequest request, String requestKey) {
        return reverse(actor, request, requestKey, false);
    }

    private MovementView reverse(FloworaPrincipal actor, ReturnRequest request, String requestKey, boolean sales) {
        MovementView replay = replay(actor.organizationId(), requestKey);
        if (replay != null) return replay;
        claim(actor.organizationId(), sales ? "M3_SALES_RETURN" : "M3_PURCHASE_RETURN", requestKey);
        MovementView source = movement(actor.organizationId(), request.sourceMovementId());
        String returnId = UUID.randomUUID().toString();
        String sourceType = sales ? "SALES_RETURN" : "PURCHASE_RETURN";
        String expectedSourceType = sales ? "SALES_DELIVERY" : "PURCHASE_RECEIPT";
        if (!expectedSourceType.equals(source.sourceType()) || !request.sourceDocumentId().equals(source.sourceId())) {
            invalid("Return source document and movement do not match");
        }
        String movementId = createMovement(actor, sourceType, sourceType, returnId, source.id(), requestKey);
        if (sales) {
            jdbc.update("""
                    INSERT INTO flowora_sales_return
                        (id,organization_id,number,sales_order_id,delivery_id,disposition,actor_user_id)
                    VALUES (?,?,?,(SELECT sales_order_id FROM flowora_sales_delivery WHERE id=?),?,?,?)
                    """, returnId, actor.organizationId(), number("SR"), request.sourceDocumentId(),
                    request.sourceDocumentId(), request.disposition().toUpperCase(Locale.ROOT), actor.userId());
        } else {
            jdbc.update("""
                    INSERT INTO flowora_purchase_return
                        (id,organization_id,number,purchase_order_id,purchase_receipt_id,actor_user_id)
                    VALUES (?,?,?,(SELECT purchase_order_id FROM flowora_purchase_receipt WHERE id=?),?,?)
                    """, returnId, actor.organizationId(), number("PRT"), request.sourceDocumentId(), request.sourceDocumentId(), actor.userId());
        }
        int sequence = 1;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReturnLineRequest line : request.lines()) {
            MovementLineView original = source.lines().stream().filter(item -> item.id().equals(line.movementLineId())).findFirst()
                    .orElseThrow(() -> new PlatformApiException(HttpStatus.BAD_REQUEST, "REFERENCE_CONFLICT", "errors.referenceConflict"));
            BigDecimal already = returned(actor.organizationId(), sales, original.sourceLineId());
            if (already.add(line.quantity()).compareTo(original.quantity()) > 0) conflict("RETURN_QUANTITY_EXCEEDED", "errors.returnQuantityExceeded");
            String warehouse = sales ? original.fromWarehouseId() : original.toWarehouseId();
            String location = sales ? original.fromLocationId() : original.toLocationId();
            if (sales) {
                inbound(actor.organizationId(), warehouse, location, original.itemId(), original.lotId(), original.serialId(), line.quantity(), original.unitCost());
                if (!clean(original.serialId()).isEmpty()) jdbc.update("UPDATE flowora_inventory_serial SET status=?,warehouse_id=?,location_id=?,version_no=version_no+1 WHERE id=?",
                        "SELLABLE".equalsIgnoreCase(request.disposition()) ? "AVAILABLE" : "QUARANTINED", warehouse, nullable(location), original.serialId());
                jdbc.update("INSERT INTO flowora_sales_return_line (id,organization_id,sales_return_id,delivery_line_id,item_id,quantity,unit_cost) VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), actor.organizationId(), returnId, original.sourceLineId(), original.itemId(), line.quantity(), original.unitCost());
                int changed = jdbc.update("UPDATE flowora_sales_delivery_line SET returned_quantity=returned_quantity+? WHERE id=? AND organization_id=? AND returned_quantity+?<=quantity",
                        line.quantity(), original.sourceLineId(), actor.organizationId(), line.quantity());
                if (changed == 0) conflict("RETURN_QUANTITY_EXCEEDED", "errors.returnQuantityExceeded");
                jdbc.update("UPDATE flowora_sales_order_line SET returned_quantity=returned_quantity+?,version_no=version_no+1 WHERE id=(SELECT sales_order_line_id FROM flowora_sales_delivery_line WHERE id=?) AND organization_id=?",
                        line.quantity(), original.sourceLineId(), actor.organizationId());
            } else {
                Balance balance = lockedBalance(actor.organizationId(), warehouse, location, original.itemId(), original.lotId(), original.serialId());
                InventoryQuantityPolicy.requireReservable(balance.onHand(), balance.reserved(), line.quantity(), balance.frozen());
                int changed = jdbc.update("UPDATE flowora_purchase_receipt_line SET returned_quantity=returned_quantity+? WHERE id=? AND organization_id=? AND returned_quantity+?<=accepted_quantity",
                        line.quantity(), original.sourceLineId(), actor.organizationId(), line.quantity());
                if (changed == 0) conflict("RETURN_QUANTITY_EXCEEDED", "errors.returnQuantityExceeded");
                jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=on_hand_quantity-?,version_no=version_no+1 WHERE id=?", line.quantity(), balance.id());
                jdbc.update("INSERT INTO flowora_purchase_return_line (id,organization_id,purchase_return_id,purchase_receipt_line_id,item_id,quantity,unit_cost) VALUES (?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(), actor.organizationId(), returnId, original.sourceLineId(), original.itemId(), line.quantity(), original.unitCost());
                jdbc.update("UPDATE flowora_purchase_order_line SET returned_quantity=returned_quantity+?,version_no=version_no+1 WHERE id=(SELECT purchase_order_line_id FROM flowora_purchase_receipt_line WHERE id=?) AND organization_id=?",
                        line.quantity(), original.sourceLineId(), actor.organizationId());
            }
            insertMovementLine(actor.organizationId(), movementId, sequence++, original.itemId(),
                    sales ? null : warehouse, sales ? null : location, sales ? warehouse : null, sales ? location : null,
                    original.lotId(), original.serialId(), line.quantity(), original.unitCost(), sourceType + "_LINE", original.sourceLineId());
            totalQuantity = totalQuantity.add(line.quantity());
            totalAmount = totalAmount.add(line.quantity().multiply(original.unitCost()));
        }
        createFinancialEvent(actor.organizationId(), sales ? "SALES_RETURN_COST_SOURCE" : "PURCHASE_RETURN_SOURCE",
                sourceType, returnId, returnCurrency(actor.organizationId(), sales, request.sourceDocumentId()),
                totalQuantity, totalAmount, Map.of("sourceMovementId", source.id()));
        return movement(actor.organizationId(), movementId);
    }

    private String insertReceiptLine(String organizationId, String receiptId, ReceiptLineRequest line, String lotId, String serialId, BigDecimal accepted, BigDecimal rejected) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_purchase_receipt_line
                    (id,organization_id,purchase_receipt_id,purchase_order_line_id,item_id,location_id,lot_id,serial_id,
                     quantity,accepted_quantity,rejected_quantity,unit_cost)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, organizationId, receiptId, line.purchaseOrderLineId(), line.itemId(), nullable(line.locationId()),
                nullable(lotId), nullable(serialId), accepted.add(rejected), accepted, rejected, line.unitCost());
        return id;
    }

    private String resolveLot(String organizationId, ReceiptLineRequest line, String tracking) {
        if (!"LOT".equals(tracking) && !"SERIAL".equals(tracking)) return "";
        if (clean(line.lotCode()).isEmpty()) conflict("TRACKING_REQUIRED", "errors.trackingRequired");
        List<String> existing = jdbc.query("SELECT id FROM flowora_inventory_lot WHERE organization_id=? AND item_id=? AND lot_code=?",
                (rs, row) -> rs.getString(1), organizationId, line.itemId(), line.lotCode().trim());
        if (!existing.isEmpty()) return existing.getFirst();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO flowora_inventory_lot (id,organization_id,item_id,lot_code,supplier_lot_code,manufactured_on,expires_on) VALUES (?,?,?,?,?,?,?)",
                id, organizationId, line.itemId(), line.lotCode().trim(), nullable(line.supplierLotCode()), line.manufacturedOn(), line.expiresOn());
        return id;
    }

    private List<String> resolveSerials(String organizationId, String warehouseId, ReceiptLineRequest line, String lotId, String tracking, BigDecimal accepted) {
        if (!"SERIAL".equals(tracking)) return List.of();
        List<String> codes = line.serialCodes() == null ? List.of() : line.serialCodes().stream().map(String::trim).filter(code -> !code.isEmpty()).distinct().toList();
        int count;
        try { count = accepted.intValueExact(); } catch (ArithmeticException exception) { conflict("TRACKING_REQUIRED", "errors.trackingRequired"); return List.of(); }
        if (codes.size() != count) conflict("TRACKING_REQUIRED", "errors.trackingRequired");
        List<String> ids = new ArrayList<>();
        for (String code : codes) {
            String id = UUID.randomUUID().toString();
            try {
                jdbc.update("INSERT INTO flowora_inventory_serial (id,organization_id,item_id,serial_code,lot_id,warehouse_id,location_id) VALUES (?,?,?,?,?,?,?)",
                        id, organizationId, line.itemId(), code, nullable(lotId), warehouseId, nullable(line.locationId()));
            } catch (DuplicateKeyException duplicate) {
                conflict("SERIAL_STATE_CONFLICT", "errors.serialStateConflict");
            }
            ids.add(id);
        }
        return ids;
    }

    private void inbound(String organizationId, String warehouseId, String locationId, String itemId, String lotId, String serialId, BigDecimal quantity, BigDecimal unitCost) {
        Balance balance = lockedBalance(organizationId, warehouseId, locationId, itemId, lotId, serialId);
        BigDecimal average = InventoryQuantityPolicy.inboundAverageCost(balance.onHand(), balance.averageCost(), quantity, unitCost);
        jdbc.update("UPDATE flowora_inventory_balance_v2 SET on_hand_quantity=on_hand_quantity+?,average_cost=?,version_no=version_no+1 WHERE id=?", quantity, average, balance.id());
    }

    private Balance lockedBalance(String organizationId, String warehouseId, String locationId, String itemId, String lotId, String serialId) {
        jdbc.update("""
                INSERT INTO flowora_inventory_balance_v2 (id,organization_id,warehouse_id,location_id,item_id,lot_id,serial_id)
                VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id
                """, UUID.randomUUID().toString(), organizationId, warehouseId, key(locationId), itemId, key(lotId), key(serialId));
        return jdbc.queryForObject("""
                SELECT id,on_hand_quantity,reserved_quantity,average_cost,frozen FROM flowora_inventory_balance_v2
                WHERE organization_id=? AND warehouse_id=? AND location_id=? AND item_id=? AND lot_id=? AND serial_id=? FOR UPDATE
                """, (rs, row) -> new Balance(rs.getString("id"), rs.getBigDecimal("on_hand_quantity"),
                rs.getBigDecimal("reserved_quantity"), rs.getBigDecimal("average_cost"), rs.getBoolean("frozen")),
                organizationId, warehouseId, key(locationId), itemId, key(lotId), key(serialId));
    }

    private String createMovement(FloworaPrincipal actor, String type, String sourceType, String sourceId, String reversalOf, String requestKey) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO flowora_stock_movement (id,organization_id,number,movement_type,source_type,source_id,reversal_of_id,actor_user_id,request_id) VALUES (?,?,?,?,?,?,?,?,?)",
                id, actor.organizationId(), number("MOV"), type, sourceType, sourceId, nullable(reversalOf), actor.userId(), requireKey(requestKey));
        return id;
    }

    private void insertMovementLine(String organizationId, String movementId, int sequence, String itemId,
                                    String fromWarehouse, String fromLocation, String toWarehouse, String toLocation,
                                    String lotId, String serialId, BigDecimal quantity, BigDecimal unitCost,
                                    String sourceLineType, String sourceLineId) {
        jdbc.update("""
                INSERT INTO flowora_stock_movement_line
                    (id,organization_id,movement_id,sequence_no,item_id,from_warehouse_id,from_location_id,
                     to_warehouse_id,to_location_id,lot_id,serial_id,quantity,unit_cost,value_amount,source_line_type,source_line_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), organizationId, movementId, sequence, itemId, nullable(fromWarehouse),
                nullable(fromLocation), nullable(toWarehouse), nullable(toLocation), nullable(lotId), nullable(serialId),
                quantity, unitCost, quantity.multiply(unitCost), sourceLineType, sourceLineId);
    }
    private MovementView movement(String organizationId, String movementId) {
        List<MovementView> rows = jdbc.query("""
                SELECT id,number,movement_type,source_type,source_id,reversal_of_id,request_id,posted_at
                FROM flowora_stock_movement WHERE id=? AND organization_id=?
                """, (rs, row) -> new MovementView(rs.getString("id"), rs.getString("number"), rs.getString("movement_type"),
                rs.getString("source_type"), rs.getString("source_id"), rs.getString("reversal_of_id"), rs.getString("request_id"),
                rs.getTimestamp("posted_at").toInstant(), movementLines(organizationId, movementId)), movementId, organizationId);
        if (rows.isEmpty()) { notFound("stockMovement", movementId); return null; }
        return rows.getFirst();
    }

    private List<MovementLineView> movementLines(String organizationId, String movementId) {
        return jdbc.query("""
                SELECT id,sequence_no,item_id,from_warehouse_id,from_location_id,to_warehouse_id,to_location_id,
                       lot_id,serial_id,quantity,unit_cost,value_amount,source_line_type,source_line_id
                FROM flowora_stock_movement_line WHERE organization_id=? AND movement_id=? ORDER BY sequence_no
                """, (rs, row) -> new MovementLineView(rs.getString("id"), rs.getInt("sequence_no"), rs.getString("item_id"),
                rs.getString("from_warehouse_id"), rs.getString("from_location_id"), rs.getString("to_warehouse_id"),
                rs.getString("to_location_id"), rs.getString("lot_id"), rs.getString("serial_id"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_cost"), rs.getBigDecimal("value_amount"), rs.getString("source_line_type"), rs.getString("source_line_id")),
                organizationId, movementId);
    }

    private Reservation lockedReservation(String organizationId, String id) {
        List<Reservation> rows = jdbc.query("""
                SELECT id,sales_order_id,sales_order_line_id,warehouse_id,location_id,item_id,lot_id,serial_id,
                       reserved_quantity,consumed_quantity
                FROM flowora_stock_reservation WHERE id=? AND organization_id=? AND status IN ('ACTIVE','PARTIAL') FOR UPDATE
                """, (rs, row) -> new Reservation(rs.getString("id"), rs.getString("sales_order_id"), rs.getString("sales_order_line_id"),
                rs.getString("warehouse_id"), rs.getString("location_id"), rs.getString("item_id"), rs.getString("lot_id"),
                rs.getString("serial_id"), rs.getBigDecimal("reserved_quantity"), rs.getBigDecimal("consumed_quantity")), id, organizationId);
        if (rows.isEmpty()) { notFound("stockReservation", id); return null; }
        return rows.getFirst();
    }

    private ReservationView reservation(String organizationId, String id) {
        return jdbc.queryForObject("SELECT * FROM flowora_stock_reservation WHERE id=? AND organization_id=?",
                (rs, row) -> new ReservationView(rs.getString("id"), rs.getString("sales_order_id"), rs.getString("sales_order_line_id"),
                        rs.getString("warehouse_id"), blankToNull(rs.getString("location_id")), rs.getString("item_id"),
                        blankToNull(rs.getString("lot_id")), blankToNull(rs.getString("serial_id")), rs.getBigDecimal("reserved_quantity"),
                        rs.getBigDecimal("consumed_quantity"), rs.getString("status"), instant(rs.getTimestamp("expires_at")), rs.getLong("version_no")), id, organizationId);
    }

    private MovementView replay(String organizationId, String requestKey) {
        if (clean(requestKey).isEmpty()) return null;
        List<String> ids = jdbc.query("SELECT id FROM flowora_stock_movement WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), organizationId, requestKey.trim());
        return ids.isEmpty() ? null : movement(organizationId, ids.getFirst());
    }

    private void claim(String organizationId, String operation, String requestKey) {
        if (!idempotency.claim(organizationId, operation, requireKey(requestKey))) conflict("IDEMPOTENCY_CONFLICT", "errors.idempotencyConflict");
    }

    private void createFinancialEvent(String organizationId, String eventType, String sourceType, String sourceId,
                                      String currency, BigDecimal quantity, BigDecimal amount, Map<String, Object> payload) {
        jdbc.update("INSERT INTO flowora_financial_source_event (id,organization_id,event_type,source_type,source_id,currency_code,quantity,amount,payload_json) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), organizationId, eventType, sourceType, sourceId, currency, quantity, amount, json(payload));
    }

    private String orderCurrency(String organizationId, boolean sales, String orderId) {
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        return jdbc.queryForObject("SELECT currency_code FROM " + table + " WHERE id=? AND organization_id=?", String.class, orderId, organizationId);
    }

    private String returnCurrency(String organizationId, boolean sales, String sourceDocumentId) {
        String sql = sales
                ? "SELECT sales.currency_code FROM flowora_sales_delivery delivery JOIN flowora_sales_order sales ON sales.id=delivery.sales_order_id WHERE delivery.id=? AND delivery.organization_id=?"
                : "SELECT purchase.currency_code FROM flowora_purchase_receipt receipt JOIN flowora_purchase_order purchase ON purchase.id=receipt.purchase_order_id WHERE receipt.id=? AND receipt.organization_id=?";
        return jdbc.queryForObject(sql, String.class, sourceDocumentId, organizationId);
    }

    private String baseCurrency(String organizationId) {
        return jdbc.queryForObject("SELECT base_currency_code FROM flowora_organization WHERE id=?", String.class, organizationId);
    }

    private void updatePurchaseOrderStatus(String organizationId, String orderId) {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_purchase_order_line WHERE organization_id=? AND purchase_order_id=? AND received_quantity<ordered_quantity", Integer.class, organizationId, orderId);
        jdbc.update("UPDATE flowora_purchase_order SET status=?,version_no=version_no+1 WHERE id=? AND organization_id=?", open != null && open == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED", orderId, organizationId);
    }

    private void updateSalesOrderStatus(String organizationId, String orderId) {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_sales_order_line WHERE organization_id=? AND sales_order_id=? AND fulfilled_quantity<ordered_quantity-cancelled_quantity", Integer.class, organizationId, orderId);
        jdbc.update("UPDATE flowora_sales_order SET status=?,version_no=version_no+1 WHERE id=? AND organization_id=?", open != null && open == 0 ? "FULFILLED" : "PARTIALLY_FULFILLED", orderId, organizationId);
    }

    private void requireOrderLine(String organizationId, String orderId, String lineId, String itemId) {
        requireCount("SELECT COUNT(*) FROM flowora_purchase_order_line WHERE id=? AND organization_id=? AND purchase_order_id=? AND item_id=?", lineId, organizationId, orderId, itemId);
    }

    private void requireSalesOrderLine(String organizationId, String orderId, String lineId, String itemId) {
        requireCount("SELECT COUNT(*) FROM flowora_sales_order_line WHERE id=? AND organization_id=? AND sales_order_id=? AND item_id=?", lineId, organizationId, orderId, itemId);
    }

    private void requireResource(String table, String id, String organizationId) {
        requireCount("SELECT COUNT(*) FROM " + table + " WHERE id=? AND organization_id=?", id, organizationId);
    }

    // Always lock the header before lines/reservations/balances. Cancellation updates the
    // same header, so the state check and all inventory changes serialize with cancellation.
    private void lockFulfillableOrder(String organizationId, boolean sales, String id, String operation) {
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        List<String> states = jdbc.query("SELECT status FROM " + table + " WHERE id=? AND organization_id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), id, organizationId);
        if (states.isEmpty()) notFound(sales ? "salesOrder" : "purchaseOrder", id);
        FulfillmentStatePolicy.requireAllowed(states.getFirst(), operation);
    }

    private void requireOrderWarehouse(String organizationId, boolean sales, String id, String warehouseId) {
        String table = sales ? "flowora_sales_order" : "flowora_purchase_order";
        String expected = jdbc.queryForObject("SELECT warehouse_id FROM " + table + " WHERE id=? AND organization_id=?",
                String.class, id, organizationId);
        if (!Objects.equals(expected, warehouseId)) invalid("Warehouse must match the order warehouse");
    }

    private void requireCount(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        if (count == null || count == 0) notFound("resource", String.valueOf(args[0]));
    }

    private String tracking(String organizationId, String itemId) {
        return jdbc.queryForObject("SELECT tracking_method FROM flowora_item WHERE id=? AND organization_id=? AND active=TRUE", String.class, itemId, organizationId);
    }

    private void requireSerialStatus(String organizationId, String serialId, String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM flowora_inventory_serial WHERE id=? AND organization_id=? AND status=?", Integer.class, serialId, organizationId, status);
        if (count == null || count == 0) conflict("SERIAL_STATE_CONFLICT", "errors.serialStateConflict");
    }

    private void requireOrderReservable(String organizationId, String orderId, String lineId, BigDecimal requested) {
        List<BigDecimal> remaining = jdbc.query("""
                SELECT ordered_quantity-fulfilled_quantity-cancelled_quantity-reserved_quantity
                FROM flowora_sales_order_line
                WHERE id=? AND organization_id=? AND sales_order_id=? FOR UPDATE
                """, (rs, row) -> rs.getBigDecimal(1), lineId, organizationId, orderId);
        if (remaining.isEmpty()) notFound("salesOrderLine", lineId);
        if (remaining.getFirst().compareTo(requested) < 0) {
            conflict("RESERVATION_QUANTITY_EXCEEDED", "errors.reservationQuantityExceeded");
        }
    }

    private BigDecimal returned(String organizationId, boolean sales, String sourceLineId) {
        String sql = sales
                ? "SELECT COALESCE(SUM(quantity),0) FROM flowora_sales_return_line WHERE organization_id=? AND delivery_line_id=?"
                : "SELECT COALESCE(SUM(quantity),0) FROM flowora_purchase_return_line WHERE organization_id=? AND purchase_receipt_line_id=?";
        return jdbc.queryForObject(sql, BigDecimal.class, organizationId, sourceLineId);
    }

    private String json(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private Map<String, Object> readMap(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { return Map.of(); }
    }
    private static String number(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String key(String value) { return clean(value); }
    private static String nullable(String value) { return clean(value).isEmpty() ? null : value.trim(); }
    private static String blankToNull(String value) { return clean(value).isEmpty() ? null : value; }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String requireKey(String value) { if (clean(value).isEmpty()) throw new PlatformApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "errors.idempotencyKeyRequired"); return value.trim(); }
    private static void invalid(String message) { throw new PlatformApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "errors.validationFailed", Map.of("reason", message)); }
    private static void conflict(String code, String key) { throw new PlatformApiException(HttpStatus.CONFLICT, code, key); }
    private static void notFound(String type, String id) { throw new PlatformApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "errors.resourceNotFound", Map.of("resourceType", type, "resourceId", id)); }

    private record Balance(String id, BigDecimal onHand, BigDecimal reserved, BigDecimal averageCost, boolean frozen) { }
    private record Reservation(String id, String salesOrderId, String salesOrderLineId, String warehouseId, String locationId,
                               String itemId, String lotId, String serialId, BigDecimal reserved, BigDecimal consumed) { }
}
