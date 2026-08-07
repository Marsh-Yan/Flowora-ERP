package com.flowora.erp.sales;

import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.inventory.InventoryService;
import com.flowora.erp.masterdata.CustomerEntity;
import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.masterdata.ItemEntity;
import com.flowora.erp.masterdata.ItemRepository;
import com.flowora.erp.masterdata.ItemType;
import com.flowora.erp.masterdata.WarehouseEntity;
import com.flowora.erp.masterdata.WarehouseRepository;
import com.flowora.erp.sales.SalesDtos.DeliveryCreate;
import com.flowora.erp.sales.SalesDtos.SalesOrderCreate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {
    @Mock private SalesQuoteRepository quoteRepository;
    @Mock private SalesQuoteLineRepository quoteLineRepository;
    @Mock private SalesOrderRepository orderRepository;
    @Mock private SalesOrderLineRepository orderLineRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryLineRepository deliveryLineRepository;
    @Mock private ReceivableRepository receivableRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private InventoryService inventoryService;
    @Mock private com.flowora.erp.workflow.WorkflowService workflowService;

    @InjectMocks
    private SalesService service;

    @Test
    void createsConfirmedOrderAndReceivableBasis() {
        FloworaPrincipal actor = actor();
        CustomerEntity customer = new CustomerEntity("org-a", "C-1", "Acme", null, null, null, null, "USD", 30, true);
        ItemEntity item = item();
        WarehouseEntity warehouse = new WarehouseEntity("org-a", "WH-1", "Main", null, true);
        when(customerRepository.findByIdAndOrganizationId("customer-a", "org-a")).thenReturn(Optional.of(customer));
        when(itemRepository.findByIdAndOrganizationId("item-a", "org-a")).thenReturn(Optional.of(item));
        when(warehouseRepository.findByIdAndOrganizationId("warehouse-a", "org-a")).thenReturn(Optional.of(warehouse));
        when(orderRepository.save(any(SalesOrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderLineRepository.save(any(SalesOrderLineEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receivableRepository.save(any(ReceivableEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SalesOrderLineEntity line = new SalesOrderLineEntity("org-a", "order-a", "item-a", new BigDecimal("3"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO);
        ReceivableEntity receivable = new ReceivableEntity("org-a", "AR-1", "order-a", "customer-a", "SALES_ORDER", "order-a", "USD", new BigDecimal("150"), LocalDate.now().plusDays(30));
        when(orderLineRepository.findFirstByOrganizationIdAndSalesOrderId(any(), any())).thenReturn(Optional.of(line));
        when(receivableRepository.findFirstByOrganizationIdAndSalesOrderId(any(), any())).thenReturn(Optional.of(receivable));

        var result = service.createOrder(actor, new SalesOrderCreate(
                null, "customer-a", "warehouse-a", "item-a", new BigDecimal("3"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO, "USD", null, null
        ));

        assertThat(result.status()).isEqualTo(SalesOrderStatus.CONFIRMED);
        assertThat(result.totalAmount()).isEqualByComparingTo("150.0000");
        verify(receivableRepository).save(any(ReceivableEntity.class));
    }

    @Test
    void partialDeliveryIssuesInventoryAndLeavesOrderOpen() {
        FloworaPrincipal actor = actor();
        SalesOrderEntity order = new SalesOrderEntity("org-a", "SO-1", null, "customer-a", "warehouse-a", SalesOrderStatus.CONFIRMED, "USD", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("500"), null, actor.userId());
        SalesOrderLineEntity line = new SalesOrderLineEntity("org-a", order.id(), "item-a", new BigDecimal("10"), new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO);
        ItemEntity item = item();
        when(orderRepository.findByIdAndOrganizationId("order-a", "org-a")).thenReturn(Optional.of(order));
        when(orderLineRepository.findByIdAndOrganizationId("line-a", "org-a")).thenReturn(Optional.of(line));
        when(itemRepository.findByIdAndOrganizationId("item-a", "org-a")).thenReturn(Optional.of(item));
        when(deliveryRepository.save(any(DeliveryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryLineRepository.save(any(DeliveryLineEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryService.issueForSales(eq(actor), eq("warehouse-a"), eq(item.id()), eq(new BigDecimal("4")), anyString())).thenReturn(new BigDecimal("12.5000"));

        var result = service.deliver(actor, new DeliveryCreate("order-a", "line-a", "warehouse-a", new BigDecimal("4")));

        assertThat(result.quantity()).isEqualByComparingTo("4");
        assertThat(result.unitCost()).isEqualByComparingTo("12.5000");
        assertThat(order.status()).isEqualTo(SalesOrderStatus.PARTIALLY_FULFILLED);
        verify(inventoryService).issueForSales(actor, "warehouse-a", item.id(), new BigDecimal("4"), result.id());
    }

    private FloworaPrincipal actor() {
        return new FloworaPrincipal("user-1", "operator@example.com", "Operator", "org-a", "Demo", List.of("BUSINESS"));
    }

    private ItemEntity item() {
        return new ItemEntity("org-a", "ITEM-1", "Item A", ItemType.GOODS, "pcs", new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO, true, true);
    }
}
