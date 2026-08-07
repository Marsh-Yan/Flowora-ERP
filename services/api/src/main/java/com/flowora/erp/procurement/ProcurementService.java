package com.flowora.erp.procurement;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.ItemEntity;
import com.flowora.erp.masterdata.ItemRepository;
import com.flowora.erp.masterdata.SupplierRepository;
import com.flowora.erp.masterdata.WarehouseRepository;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseOrderCreate;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseOrderResponse;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseRequestCreate;
import com.flowora.erp.procurement.ProcurementDtos.PurchaseRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProcurementService {
    private final PurchaseRequestRepository requestRepository;
    private final PurchaseRequestLineRepository requestLineRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderLineRepository orderLineRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;

    public ProcurementService(
            PurchaseRequestRepository requestRepository,
            PurchaseRequestLineRepository requestLineRepository,
            PurchaseOrderRepository orderRepository,
            PurchaseOrderLineRepository orderLineRepository,
            SupplierRepository supplierRepository,
            WarehouseRepository warehouseRepository,
            ItemRepository itemRepository
    ) {
        this.requestRepository = requestRepository;
        this.requestLineRepository = requestLineRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequestResponse> requests(String organizationId, String query, Pageable pageable) {
        Page<PurchaseRequestEntity> page = requestRepository.search(organizationId, clean(query), pageable);
        return PageResponse.from(page.map(request -> requestResponse(request, requestLineRepository.findFirstByOrganizationIdAndPurchaseRequestId(organizationId, request.id()).orElse(null))));
    }

    @Transactional
    public PurchaseRequestResponse createRequest(FloworaPrincipal actor, PurchaseRequestCreate body) {
        requireSupplier(actor.organizationId(), body.supplierId());
        requireWarehouse(actor.organizationId(), body.warehouseId());
        requireInventoryItem(actor.organizationId(), body.itemId());
        PurchaseRequestEntity request = new PurchaseRequestEntity(
                actor.organizationId(), nextNumber("PR"), clean(body.supplierId()), clean(body.warehouseId()), actor.userId(), clean(body.note())
        );
        request.submit();
        request.approve();
        requestRepository.save(request);
        PurchaseRequestLineEntity line = requestLineRepository.save(new PurchaseRequestLineEntity(
                actor.organizationId(), request.id(), clean(body.itemId()), body.quantity(), body.estimatedUnitCost()
        ));
        return requestResponse(request, line);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> orders(String organizationId, String query, Pageable pageable) {
        Page<PurchaseOrderEntity> page = orderRepository.search(organizationId, clean(query), pageable);
        return PageResponse.from(page.map(order -> orderResponse(order, orderLineRepository.findFirstByOrganizationIdAndPurchaseOrderId(organizationId, order.id()).orElse(null))));
    }

    @Transactional
    public PurchaseOrderResponse createOrder(FloworaPrincipal actor, PurchaseOrderCreate body) {
        requireSupplier(actor.organizationId(), body.supplierId());
        requireWarehouse(actor.organizationId(), body.warehouseId());
        requireInventoryItem(actor.organizationId(), body.itemId());
        if (body.purchaseRequestId() != null && !body.purchaseRequestId().isBlank()) {
            PurchaseRequestEntity request = requestRepository.findByIdAndOrganizationId(body.purchaseRequestId(), actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("purchaseRequest", body.purchaseRequestId()));
            if (request.status() != ProcurementDocumentStatus.APPROVED) {
                throw new IllegalStateException("Purchase request must be approved before creating a purchase order");
            }
        }
        PurchaseOrderEntity order = orderRepository.save(new PurchaseOrderEntity(
                actor.organizationId(), nextNumber("PO"), clean(body.purchaseRequestId()), clean(body.supplierId()), clean(body.warehouseId()), actor.userId(), body.expectedDate(), clean(body.note())
        ));
        PurchaseOrderLineEntity line = orderLineRepository.save(new PurchaseOrderLineEntity(
                actor.organizationId(), order.id(), clean(body.itemId()), body.quantity(), body.unitPrice(), body.taxRate()
        ));
        return orderResponse(order, line);
    }

    @Transactional
    public void cancelOrder(FloworaPrincipal actor, String orderId) {
        PurchaseOrderEntity order = orderRepository.findByIdAndOrganizationId(orderId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("purchaseOrder", orderId));
        if (order.status() == ProcurementDocumentStatus.RECEIVED || order.status() == ProcurementDocumentStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("A received purchase order cannot be cancelled");
        }
        order.cancel();
        orderRepository.save(order);
    }

    private void requireSupplier(String organizationId, String supplierId) {
        supplierRepository.findByIdAndOrganizationId(clean(supplierId), organizationId)
                .filter(entity -> entity.active())
                .orElseThrow(() -> new ResourceNotFoundException("supplier", supplierId));
    }

    private void requireWarehouse(String organizationId, String warehouseId) {
        warehouseRepository.findByIdAndOrganizationId(clean(warehouseId), organizationId)
                .filter(entity -> entity.active())
                .orElseThrow(() -> new ResourceNotFoundException("warehouse", warehouseId));
    }

    private ItemEntity requireInventoryItem(String organizationId, String itemId) {
        return itemRepository.findByIdAndOrganizationId(clean(itemId), organizationId)
                .filter(entity -> entity.active() && entity.inventoryManaged())
                .orElseThrow(() -> new ResourceNotFoundException("inventoryItem", itemId));
    }

    private PurchaseRequestResponse requestResponse(PurchaseRequestEntity request, PurchaseRequestLineEntity line) {
        return new PurchaseRequestResponse(request.id(), request.number(), request.status(), request.supplierId(), request.warehouseId(), request.requesterUserId(), line == null ? null : line.itemId(), line == null ? BigDecimal.ZERO : line.quantity(), line == null ? BigDecimal.ZERO : line.estimatedUnitCost(), request.note());
    }

    private PurchaseOrderResponse orderResponse(PurchaseOrderEntity order, PurchaseOrderLineEntity line) {
        return new PurchaseOrderResponse(order.id(), order.number(), order.status(), order.purchaseRequestId(), order.supplierId(), order.warehouseId(), order.buyerUserId(), line == null ? null : line.id(), line == null ? null : line.itemId(), line == null ? BigDecimal.ZERO : line.orderedQuantity(), line == null ? BigDecimal.ZERO : line.receivedQuantity(), line == null ? BigDecimal.ZERO : line.remainingQuantity(), line == null ? BigDecimal.ZERO : line.unitPrice(), line == null ? BigDecimal.ZERO : line.taxRate(), order.orderDate(), order.expectedDate(), order.note());
    }

    private String nextNumber(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
