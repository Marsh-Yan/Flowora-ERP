package com.flowora.erp.inventory;

import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.ItemEntity;
import com.flowora.erp.masterdata.ItemRepository;
import com.flowora.erp.masterdata.ItemType;
import com.flowora.erp.masterdata.OrganizationEntity;
import com.flowora.erp.masterdata.OrganizationRepository;
import com.flowora.erp.masterdata.WarehouseEntity;
import com.flowora.erp.masterdata.WarehouseRepository;
import com.flowora.erp.workflow.ApprovalPolicy;
import com.flowora.erp.workflow.WorkflowDtos.TaskResponse;
import com.flowora.erp.inventory.InventoryDtos.StockAdjustmentResponse;
import com.flowora.erp.workflow.WorkflowResourceType;
import com.flowora.erp.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock private com.flowora.erp.finance.AccountingService accountingService;
    @Mock private com.flowora.erp.procurement.PurchaseOrderRepository orderRepository;
    @Mock private com.flowora.erp.procurement.PurchaseOrderLineRepository orderLineRepository;
    @Mock private PurchaseReceiptRepository receiptRepository;
    @Mock private PurchaseReceiptLineRepository receiptLineRepository;
    @Mock private StockBalanceRepository balanceRepository;
    @Mock private StockLedgerEntryRepository ledgerRepository;
    @Mock private StockTransferRepository transferRepository;
    @Mock private StockTransferLineRepository transferLineRepository;
    @Mock private StockCountRepository countRepository;
    @Mock private StockCountLineRepository countLineRepository;
    @Mock private StockAdjustmentRepository adjustmentRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ApprovalPolicy approvalPolicy;
    @Mock private WorkflowService workflowService;

    @InjectMocks
    private InventoryService service;

    @Test
    void postsLowRiskAdjustmentAndWritesTheLedgerInOneServiceFlow() {
        FloworaPrincipal actor = actor();
        when(warehouseRepository.findByIdAndOrganizationId("warehouse-a", "org-a")).thenReturn(Optional.of(warehouse()));
        when(itemRepository.findByIdAndOrganizationId("item-a", "org-a")).thenReturn(Optional.of(item()));
        when(organizationRepository.findById("org-a")).thenReturn(Optional.of(new OrganizationEntity("org-a", "Demo", "USD", "UTC", new BigDecimal("10000"), BigDecimal.ZERO)));
        when(approvalPolicy.requiresApproval(any(), any(), any())).thenReturn(false);
        when(adjustmentRepository.save(any(StockAdjustmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceRepository.findForUpdate("org-a", "warehouse-a", "item-a")).thenReturn(Optional.of(new StockBalanceEntity("org-a", "warehouse-a", "item-a", new BigDecimal("10"), new BigDecimal("5"))));
        when(ledgerRepository.existsByOrganizationIdAndDocumentTypeAndDocumentIdAndMovementType(any(), any(), any(), any())).thenReturn(false);
        when(balanceRepository.save(any(StockBalanceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(StockLedgerEntryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockAdjustmentResponse result = service.createAdjustment(actor, new InventoryDtos.StockAdjustmentRequest(
                "warehouse-a", "item-a", new BigDecimal("2"), new BigDecimal("8"), "Cycle count variance"
        ), "req-1");

        assertThat(result.status()).isEqualTo(StockAdjustmentStatus.POSTED);
        verify(ledgerRepository).save(any(StockLedgerEntryEntity.class));
    }

    @Test
    void highRiskAdjustmentCreatesAnApprovalTaskAndDoesNotPostStock() {
        FloworaPrincipal actor = actor();
        when(warehouseRepository.findByIdAndOrganizationId("warehouse-a", "org-a")).thenReturn(Optional.of(warehouse()));
        when(itemRepository.findByIdAndOrganizationId("item-a", "org-a")).thenReturn(Optional.of(item()));
        when(organizationRepository.findById("org-a")).thenReturn(Optional.of(new OrganizationEntity("org-a", "Demo", "USD", "UTC", new BigDecimal("10000"), BigDecimal.ZERO)));
        when(approvalPolicy.requiresApproval(any(), any(), any())).thenReturn(true);
        when(adjustmentRepository.save(any(StockAdjustmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowService.createTask(any(), any(), any())).thenReturn(new TaskResponse("task-1", WorkflowResourceType.INVENTORY_ADJUSTMENT, "adjustment", "Approve adjustment", "", new BigDecimal("12000"), "user-1", null, "MANAGEMENT", com.flowora.erp.workflow.WorkflowTaskStatus.OPEN, null, null));

        StockAdjustmentResponse result = service.createAdjustment(actor, new InventoryDtos.StockAdjustmentRequest(
                "warehouse-a", "item-a", new BigDecimal("2"), new BigDecimal("6000"), "High value variance"
        ), "req-2");

        assertThat(result.status()).isEqualTo(StockAdjustmentStatus.PENDING_APPROVAL);
        assertThat(result.workflowTaskId()).isEqualTo("task-1");
        verify(workflowService).createTask(any(), any(), any());
    }

    private FloworaPrincipal actor() {
        return new FloworaPrincipal("user-1", "warehouse@example.com", "Warehouse", "org-a", "Demo", List.of("WAREHOUSE"));
    }

    private WarehouseEntity warehouse() {
        return new WarehouseEntity("org-a", "WH-A", "Main", null, true);
    }

    private ItemEntity item() {
        return new ItemEntity("org-a", "ITEM-A", "Item A", ItemType.GOODS, "pcs", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, true, true);
    }
}
