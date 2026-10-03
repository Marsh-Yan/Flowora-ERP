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
    @Mock private com.flowora.erp.finance.AccountingService accountingService;
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

    @Mock private com.flowora.erp.masterdata.OrganizationRepository organizationRepository;
    @org.mockito.Spy private QuoteValidityPolicy quoteValidity = new QuoteValidityPolicy(java.time.Clock.fixed(
            java.time.Instant.parse("2026-10-03T00:30:00Z"), java.time.ZoneOffset.UTC));

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

    @Test
    void quoteSourceRequiresReadPermissionBeforeAnyResourceLookup() {
        var actor=new FloworaPrincipal("user","user","User","org","Org","membership",null,
                com.flowora.erp.identity.DataScope.ALL,List.of("CUSTOM"),List.of("sales:create","sales:submit"),false);
        var body=new SalesOrderCreate("quote","customer","warehouse","item",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,"USD",null,null);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.createOrder(actor,body))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(customerRepository,warehouseRepository,itemRepository,quoteRepository,orderRepository,receivableRepository,accountingService);
    }

    void organizationTimezone(String timezone) {
        when(organizationRepository.findById("org-a")).thenReturn(Optional.of(
                new com.flowora.erp.masterdata.OrganizationEntity("org-a", "Demo", "USD", timezone, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    void quoteListingReportsEligibilityUsingOrganizationDate() {
        var quote = new SalesQuoteEntity("org-a", "QT-1", "customer-a", SalesQuoteStatus.APPROVED,
                "USD", LocalDate.of(2026,10,2), BigDecimal.TEN, null, "user");
        var line = new SalesQuoteLineEntity("org-a", quote.id(), "item-a", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        when(quoteRepository.search(eq("org-a"), eq(""), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(quote)));
        when(quoteLineRepository.findFirstByOrganizationIdAndQuoteId("org-a", quote.id())).thenReturn(Optional.of(line));
        organizationTimezone("America/Los_Angeles");
        assertThat(service.quotes("org-a", "", org.springframework.data.domain.PageRequest.of(0,20)).content().getFirst().sourceEligible()).isTrue();
        organizationTimezone("Asia/Shanghai");
        assertThat(service.quotes("org-a", "", org.springframework.data.domain.PageRequest.of(0,20)).content().getFirst().sourceEligible()).isFalse();
        assertThat(quote.status()).isEqualTo(SalesQuoteStatus.APPROVED);
    }

    @Test
    void compatibilityOrderRejectsExpiredQuoteBeforeWriting() {
        var authorized = new FloworaPrincipal("user","user","User","org-a","Org","membership",null,
                com.flowora.erp.identity.DataScope.ALL,List.of("CUSTOM"),List.of("sales:create","sales:submit","sales:view"),false);
        when(customerRepository.findByIdAndOrganizationId("customer-a", "org-a")).thenReturn(Optional.of(
                new CustomerEntity("org-a","C-1","Acme",null,null,null,null,"USD",30,true)));
        when(itemRepository.findByIdAndOrganizationId("item-a", "org-a")).thenReturn(Optional.of(item()));
        when(warehouseRepository.findByIdAndOrganizationId("warehouse-a", "org-a")).thenReturn(Optional.of(new WarehouseEntity("org-a","W-1","Main",null,true)));
        var quote = new SalesQuoteEntity("org-a", "QT-1", "customer-a", SalesQuoteStatus.APPROVED,
                "USD", LocalDate.of(2026,10,2), BigDecimal.TEN, null, "user");
        when(quoteRepository.findByIdAndOrganizationId("quote", "org-a")).thenReturn(Optional.of(quote));
        organizationTimezone("Asia/Shanghai");
        var body = new SalesOrderCreate("quote","customer-a","warehouse-a","item-a",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"USD",null,null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createOrder(authorized,body))
                .isInstanceOfSatisfying(com.flowora.erp.common.api.PlatformApiException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_QUOTE_EXPIRED"));
        org.mockito.Mockito.verifyNoInteractions(orderRepository,orderLineRepository,receivableRepository,accountingService);
    }

    private FloworaPrincipal actor() {
        return new FloworaPrincipal("user-1", "operator@example.com", "Operator", "org-a", "Demo", List.of("BUSINESS"));
    }

    private ItemEntity item() {
        return new ItemEntity("org-a", "ITEM-1", "Item A", ItemType.GOODS, "pcs", new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO, true, true);
    }
}
