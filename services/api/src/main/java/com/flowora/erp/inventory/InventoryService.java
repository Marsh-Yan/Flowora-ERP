package com.flowora.erp.inventory;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.ItemRepository;
import com.flowora.erp.masterdata.OrganizationEntity;
import com.flowora.erp.masterdata.OrganizationRepository;
import com.flowora.erp.masterdata.WarehouseRepository;
import com.flowora.erp.procurement.ProcurementDocumentStatus;
import com.flowora.erp.procurement.PurchaseOrderEntity;
import com.flowora.erp.procurement.PurchaseOrderLineEntity;
import com.flowora.erp.procurement.PurchaseOrderLineRepository;
import com.flowora.erp.procurement.PurchaseOrderRepository;
import com.flowora.erp.workflow.ApprovalPolicy;
import com.flowora.erp.workflow.WorkflowAction;
import com.flowora.erp.workflow.WorkflowDtos.ActionRequest;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.WorkflowService;
import com.flowora.erp.workflow.WorkflowResourceType;
import com.flowora.erp.workflow.WorkflowTaskStatus;
import com.flowora.erp.inventory.InventoryDtos.PurchaseReceiptRequest;
import com.flowora.erp.inventory.InventoryDtos.PurchaseReceiptResponse;
import com.flowora.erp.inventory.InventoryDtos.StockAdjustmentRequest;
import com.flowora.erp.inventory.InventoryDtos.StockAdjustmentResponse;
import com.flowora.erp.inventory.InventoryDtos.StockBalanceResponse;
import com.flowora.erp.inventory.InventoryDtos.StockCountRequest;
import com.flowora.erp.inventory.InventoryDtos.StockCountResponse;
import com.flowora.erp.inventory.InventoryDtos.StockLedgerResponse;
import com.flowora.erp.inventory.InventoryDtos.StockTransferRequest;
import com.flowora.erp.inventory.InventoryDtos.StockTransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class InventoryService {
    private static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("10000.0000");

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderLineRepository orderLineRepository;
    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReceiptLineRepository receiptLineRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockLedgerEntryRepository ledgerRepository;
    private final StockTransferRepository transferRepository;
    private final StockTransferLineRepository transferLineRepository;
    private final StockCountRepository countRepository;
    private final StockCountLineRepository countLineRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final OrganizationRepository organizationRepository;
    private final ApprovalPolicy approvalPolicy;
    private final WorkflowService workflowService;

    public InventoryService(
            PurchaseOrderRepository orderRepository,
            PurchaseOrderLineRepository orderLineRepository,
            PurchaseReceiptRepository receiptRepository,
            PurchaseReceiptLineRepository receiptLineRepository,
            StockBalanceRepository balanceRepository,
            StockLedgerEntryRepository ledgerRepository,
            StockTransferRepository transferRepository,
            StockTransferLineRepository transferLineRepository,
            StockCountRepository countRepository,
            StockCountLineRepository countLineRepository,
            StockAdjustmentRepository adjustmentRepository,
            WarehouseRepository warehouseRepository,
            ItemRepository itemRepository,
            OrganizationRepository organizationRepository,
            ApprovalPolicy approvalPolicy,
            WorkflowService workflowService
    ) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.receiptRepository = receiptRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.transferRepository = transferRepository;
        this.transferLineRepository = transferLineRepository;
        this.countRepository = countRepository;
        this.countLineRepository = countLineRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.warehouseRepository = warehouseRepository;
        this.itemRepository = itemRepository;
        this.organizationRepository = organizationRepository;
        this.approvalPolicy = approvalPolicy;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockBalanceResponse> balances(String organizationId, String warehouseId, Pageable pageable) {
        Page<StockBalanceEntity> page = balanceRepository.search(organizationId, clean(warehouseId), pageable);
        return PageResponse.from(page.map(this::balanceResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockLedgerResponse> ledger(String organizationId, String warehouseId, String itemId, Pageable pageable) {
        return PageResponse.from(ledgerRepository.search(organizationId, clean(warehouseId), clean(itemId), pageable).map(this::ledgerResponse));
    }

    @Transactional
    public PurchaseReceiptResponse receive(FloworaPrincipal actor, PurchaseReceiptRequest body) {
        PurchaseOrderEntity order = orderRepository.findByIdAndOrganizationId(body.purchaseOrderId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("purchaseOrder", body.purchaseOrderId()));
        if (!order.warehouseId().equals(body.warehouseId())) {
            throw new IllegalArgumentException("Receipt warehouse must match the purchase order warehouse");
        }
        if (order.status() == ProcurementDocumentStatus.CANCELLED || order.status() == ProcurementDocumentStatus.RECEIVED) {
            throw new IllegalStateException("Purchase order is not open for receiving");
        }
        PurchaseOrderLineEntity orderLine = orderLineRepository.findByIdAndOrganizationId(body.purchaseOrderLineId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("purchaseOrderLine", body.purchaseOrderLineId()));
        if (!orderLine.purchaseOrderId().equals(order.id())) {
            throw new IllegalArgumentException("Purchase order line does not belong to the purchase order");
        }
        orderLine.receive(body.quantity());
        PurchaseReceiptEntity receipt = receiptRepository.save(new PurchaseReceiptEntity(
                actor.organizationId(), nextNumber("GR"), order.id(), order.warehouseId(), actor.userId()
        ));
        PurchaseReceiptLineEntity receiptLine = receiptLineRepository.save(new PurchaseReceiptLineEntity(
                actor.organizationId(), receipt.id(), orderLine.id(), orderLine.itemId(), body.quantity(), body.unitCost()
        ));
        applyDelta(actor.organizationId(), order.warehouseId(), orderLine.itemId(), body.quantity(), body.unitCost(), InventoryMovementType.RECEIPT, "PURCHASE_RECEIPT", receipt.id(), actor.userId());
        orderLineRepository.save(orderLine);
        if (orderLine.remainingQuantity().signum() == 0) order.markReceived(); else order.markPartiallyReceived();
        orderRepository.save(order);
        return new PurchaseReceiptResponse(receipt.id(), receipt.number(), receipt.purchaseOrderId(), receipt.warehouseId(), receiptLine.itemId(), receiptLine.quantity(), receiptLine.unitCost(), receipt.receivedAt());
    }

    @Transactional
    public StockTransferResponse transfer(FloworaPrincipal actor, StockTransferRequest body) {
        if (body.sourceWarehouseId().equals(body.targetWarehouseId())) {
            throw new IllegalArgumentException("Source and target warehouses must be different");
        }
        requireWarehouse(actor.organizationId(), body.sourceWarehouseId());
        requireWarehouse(actor.organizationId(), body.targetWarehouseId());
        requireItem(actor.organizationId(), body.itemId());
        StockBalanceEntity source = lockedBalance(actor.organizationId(), body.sourceWarehouseId(), body.itemId(), body.unitCost());
        BigDecimal transferCost = source.averageCost();
        StockTransferEntity transfer = transferRepository.save(new StockTransferEntity(
                actor.organizationId(), nextNumber("TR"), body.sourceWarehouseId(), body.targetWarehouseId(), actor.userId()
        ));
        transferLineRepository.save(new StockTransferLineEntity(actor.organizationId(), transfer.id(), body.itemId(), body.quantity(), transferCost));
        applyDelta(actor.organizationId(), body.sourceWarehouseId(), body.itemId(), body.quantity().negate(), transferCost, InventoryMovementType.TRANSFER_OUT, "STOCK_TRANSFER", transfer.id(), actor.userId());
        applyDelta(actor.organizationId(), body.targetWarehouseId(), body.itemId(), body.quantity(), transferCost, InventoryMovementType.TRANSFER_IN, "STOCK_TRANSFER", transfer.id(), actor.userId());
        return new StockTransferResponse(transfer.id(), transfer.number(), transfer.sourceWarehouseId(), transfer.targetWarehouseId(), body.itemId(), body.quantity(), transfer.status());
    }

    @Transactional
    public StockCountResponse count(FloworaPrincipal actor, StockCountRequest body) {
        requireWarehouse(actor.organizationId(), body.warehouseId());
        requireItem(actor.organizationId(), body.itemId());
        StockBalanceEntity balance = lockedBalance(actor.organizationId(), body.warehouseId(), body.itemId(), body.unitCost());
        BigDecimal expected = balance.quantity();
        StockCountEntity count = countRepository.save(new StockCountEntity(actor.organizationId(), nextNumber("SC"), body.warehouseId(), actor.userId()));
        StockCountLineEntity line = countLineRepository.save(new StockCountLineEntity(actor.organizationId(), count.id(), body.itemId(), expected, body.countedQuantity(), body.unitCost()));
        BigDecimal variance = line.variance();
        if (variance.signum() != 0) {
            applyDelta(actor.organizationId(), body.warehouseId(), body.itemId(), variance, variance.signum() > 0 ? body.unitCost() : balance.averageCost(), InventoryMovementType.COUNT, "STOCK_COUNT", count.id(), actor.userId());
        }
        return new StockCountResponse(count.id(), count.number(), count.warehouseId(), line.itemId(), line.expectedQuantity(), line.countedQuantity(), variance, count.status());
    }

    @Transactional
    public StockAdjustmentResponse createAdjustment(FloworaPrincipal actor, StockAdjustmentRequest body, String requestId) {
        requireWarehouse(actor.organizationId(), body.warehouseId());
        requireItem(actor.organizationId(), body.itemId());
        StockAdjustmentEntity adjustment = adjustmentRepository.save(new StockAdjustmentEntity(
                actor.organizationId(), nextNumber("ADJ"), body.warehouseId(), body.itemId(), body.quantityDelta(), body.unitCost(), clean(body.reason()), actor.userId()
        ));
        BigDecimal amount = body.quantityDelta().abs().multiply(body.unitCost());
        if (approvalPolicy.requiresApproval(WorkflowResourceType.INVENTORY_ADJUSTMENT, amount, approvalThreshold(actor.organizationId()))) {
            var task = workflowService.createTask(actor, new TaskRequest(
                    WorkflowResourceType.INVENTORY_ADJUSTMENT, adjustment.id(), "Approve stock adjustment " + adjustment.number(), body.reason(), amount, null, null
            ), requestId);
            adjustment.pendingApproval(task.id());
            adjustmentRepository.save(adjustment);
        } else {
            postAdjustment(actor, adjustment);
        }
        return adjustmentResponse(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse approveAdjustment(FloworaPrincipal actor, String adjustmentId, String requestId) {
        StockAdjustmentEntity adjustment = adjustmentRepository.findByIdAndOrganizationId(adjustmentId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("stockAdjustment", adjustmentId));
        if (adjustment.status() != StockAdjustmentStatus.PENDING_APPROVAL || adjustment.workflowTaskId() == null) {
            throw new IllegalStateException("Stock adjustment is not awaiting approval");
        }
        var result = workflowService.act(actor, adjustment.workflowTaskId(), new ActionRequest(WorkflowAction.APPROVE, null, null), requestId);
        if (result.task().status() == WorkflowTaskStatus.APPROVED) postAdjustment(actor, adjustment);
        return adjustmentResponse(adjustment);
    }

    @Transactional
    public StockAdjustmentResponse rejectAdjustment(FloworaPrincipal actor, String adjustmentId, String requestId) {
        StockAdjustmentEntity adjustment = adjustmentRepository.findByIdAndOrganizationId(adjustmentId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("stockAdjustment", adjustmentId));
        if (adjustment.status() != StockAdjustmentStatus.PENDING_APPROVAL || adjustment.workflowTaskId() == null) {
            throw new IllegalStateException("Stock adjustment is not awaiting approval");
        }
        workflowService.act(actor, adjustment.workflowTaskId(), new ActionRequest(WorkflowAction.REJECT, null, null), requestId);
        adjustment.reject();
        return adjustmentResponse(adjustmentRepository.save(adjustment));
    }

    private void postAdjustment(FloworaPrincipal actor, StockAdjustmentEntity adjustment) {
        if (adjustment.status() == StockAdjustmentStatus.POSTED) return;
        applyDelta(actor.organizationId(), adjustment.warehouseId(), adjustment.itemId(), adjustment.quantityDelta(), adjustment.unitCost(), InventoryMovementType.ADJUSTMENT, "STOCK_ADJUSTMENT", adjustment.id(), actor.userId());
        adjustment.post();
        adjustmentRepository.save(adjustment);
    }

    private StockBalanceEntity lockedBalance(String organizationId, String warehouseId, String itemId, BigDecimal defaultCost) {
        return balanceRepository.findForUpdate(organizationId, warehouseId, itemId)
                .orElseGet(() -> balanceRepository.save(new StockBalanceEntity(organizationId, warehouseId, itemId, BigDecimal.ZERO, defaultCost)));
    }

    private void applyDelta(String organizationId, String warehouseId, String itemId, BigDecimal quantityDelta, BigDecimal unitCost, InventoryMovementType movementType, String documentType, String documentId, String actorUserId) {
        if (ledgerRepository.existsByOrganizationIdAndDocumentTypeAndDocumentIdAndMovementType(organizationId, documentType, documentId, movementType)) return;
        StockBalanceEntity balance = lockedBalance(organizationId, warehouseId, itemId, unitCost);
        BigDecimal oldAverageCost = balance.averageCost();
        BigDecimal valueDelta;
        if (quantityDelta.signum() > 0) {
            balance.receive(quantityDelta, unitCost);
            valueDelta = quantityDelta.multiply(unitCost);
        } else {
            BigDecimal decrease = quantityDelta.abs();
            balance.decrease(decrease);
            valueDelta = decrease.multiply(oldAverageCost).negate();
        }
        balanceRepository.save(balance);
        ledgerRepository.save(new StockLedgerEntryEntity(
                organizationId, warehouseId, itemId, movementType, documentType, documentId, quantityDelta, quantityDelta.signum() > 0 ? unitCost : oldAverageCost, valueDelta, balance.quantity(), balance.inventoryValue(), actorUserId
        ));
    }

    private BigDecimal approvalThreshold(String organizationId) {
        return organizationRepository.findById(organizationId).map(OrganizationEntity::approvalThreshold).orElse(DEFAULT_APPROVAL_THRESHOLD);
    }

    private void requireWarehouse(String organizationId, String warehouseId) {
        warehouseRepository.findByIdAndOrganizationId(clean(warehouseId), organizationId).filter(entity -> entity.active()).orElseThrow(() -> new ResourceNotFoundException("warehouse", warehouseId));
    }

    private void requireItem(String organizationId, String itemId) {
        itemRepository.findByIdAndOrganizationId(clean(itemId), organizationId).filter(entity -> entity.active() && entity.inventoryManaged()).orElseThrow(() -> new ResourceNotFoundException("inventoryItem", itemId));
    }

    private StockBalanceResponse balanceResponse(StockBalanceEntity entity) {
        return new StockBalanceResponse(entity.id(), entity.warehouseId(), entity.itemId(), entity.quantity(), entity.averageCost(), entity.inventoryValue());
    }

    private StockLedgerResponse ledgerResponse(StockLedgerEntryEntity entity) {
        return new StockLedgerResponse(entity.id(), entity.warehouseId(), entity.itemId(), entity.movementType(), entity.documentType(), entity.documentId(), entity.quantityDelta(), entity.unitCost(), entity.valueDelta(), entity.balanceQuantity(), entity.balanceValue(), entity.actorUserId(), entity.createdAt());
    }

    private PurchaseReceiptResponse receiptResponse(PurchaseReceiptEntity receipt, PurchaseReceiptLineEntity line) {
        return new PurchaseReceiptResponse(receipt.id(), receipt.number(), receipt.purchaseOrderId(), receipt.warehouseId(), line.itemId(), line.quantity(), line.unitCost(), receipt.receivedAt());
    }

    private StockAdjustmentResponse adjustmentResponse(StockAdjustmentEntity entity) {
        return new StockAdjustmentResponse(entity.id(), entity.number(), entity.warehouseId(), entity.itemId(), entity.quantityDelta(), entity.unitCost(), entity.reason(), entity.status(), entity.workflowTaskId(), entity.postedAt());
    }

    private String nextNumber(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
