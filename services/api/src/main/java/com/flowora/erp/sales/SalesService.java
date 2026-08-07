package com.flowora.erp.sales;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.finance.AccountingService;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.inventory.InventoryService;
import com.flowora.erp.masterdata.CustomerEntity;
import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.masterdata.ItemEntity;
import com.flowora.erp.masterdata.ItemRepository;
import com.flowora.erp.masterdata.WarehouseRepository;
import com.flowora.erp.workflow.WorkflowAction;
import com.flowora.erp.workflow.WorkflowDtos.ActionRequest;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.WorkflowResourceType;
import com.flowora.erp.workflow.WorkflowService;
import com.flowora.erp.workflow.WorkflowTaskStatus;
import com.flowora.erp.sales.SalesDtos.DeliveryCreate;
import com.flowora.erp.sales.SalesDtos.DeliveryResponse;
import com.flowora.erp.sales.SalesDtos.PaymentCreate;
import com.flowora.erp.sales.SalesDtos.PaymentResponse;
import com.flowora.erp.sales.SalesDtos.ReceivableResponse;
import com.flowora.erp.sales.SalesDtos.SalesOrderCreate;
import com.flowora.erp.sales.SalesDtos.SalesOrderResponse;
import com.flowora.erp.sales.SalesDtos.SalesQuoteCreate;
import com.flowora.erp.sales.SalesDtos.SalesQuoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class SalesService {
    private final SalesQuoteRepository quoteRepository;
    private final SalesQuoteLineRepository quoteLineRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderLineRepository orderLineRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryLineRepository deliveryLineRepository;
    private final ReceivableRepository receivableRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final WorkflowService workflowService;
    private final AccountingService accountingService;

    public SalesService(
            SalesQuoteRepository quoteRepository,
            SalesQuoteLineRepository quoteLineRepository,
            SalesOrderRepository orderRepository,
            SalesOrderLineRepository orderLineRepository,
            DeliveryRepository deliveryRepository,
            DeliveryLineRepository deliveryLineRepository,
            ReceivableRepository receivableRepository,
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            ItemRepository itemRepository,
            WarehouseRepository warehouseRepository,
            InventoryService inventoryService,
            WorkflowService workflowService,
            AccountingService accountingService
    ) {
        this.quoteRepository = quoteRepository;
        this.quoteLineRepository = quoteLineRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.receivableRepository = receivableRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryService = inventoryService;
        this.workflowService = workflowService;
        this.accountingService = accountingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesQuoteResponse> quotes(String organizationId, String query, Pageable pageable) {
        Page<SalesQuoteEntity> page = quoteRepository.search(organizationId, clean(query), pageable);
        return PageResponse.from(page.map(this::quoteResponse));
    }

    @Transactional
    public SalesQuoteResponse createQuote(FloworaPrincipal actor, SalesQuoteCreate body, String requestId) {
        CustomerEntity customer = requireCustomer(actor.organizationId(), body.customerId());
        requireItem(actor.organizationId(), body.itemId());
        if (body.validUntil().isBefore(LocalDate.now())) throw new IllegalArgumentException("Quote validity date cannot be in the past");
        BigDecimal total = SalesQuoteLineEntity.total(body.quantity(), body.unitPrice(), body.discountRate(), body.taxRate());
        SalesQuoteEntity quote = quoteRepository.save(new SalesQuoteEntity(
                actor.organizationId(), nextNumber("QT"), customer.id(), SalesQuoteStatus.DRAFT, clean(body.currencyCode()).toUpperCase(), body.validUntil(), total, clean(body.note()), actor.userId()
        ));
        quoteLineRepository.save(new SalesQuoteLineEntity(actor.organizationId(), quote.id(), body.itemId(), body.quantity(), body.unitPrice(), body.discountRate(), body.taxRate()));
        var task = workflowService.createTask(actor, new TaskRequest(
                WorkflowResourceType.SALES_QUOTE, quote.id(), "Approve sales quote " + quote.number(), body.note(), total, null, null
        ), requestId);
        if (task.status() == WorkflowTaskStatus.APPROVED) quote.approve(); else quote.submitForApproval(task.id());
        return quoteResponse(quoteRepository.save(quote));
    }

    @Transactional
    public SalesQuoteResponse approveQuote(FloworaPrincipal actor, String quoteId, String requestId) {
        SalesQuoteEntity quote = quoteRepository.findByIdAndOrganizationId(quoteId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("salesQuote", quoteId));
        if (quote.status() != SalesQuoteStatus.SUBMITTED || quote.workflowTaskId() == null) throw new IllegalStateException("Sales quote is not awaiting approval");
        var result = workflowService.act(actor, quote.workflowTaskId(), new ActionRequest(WorkflowAction.APPROVE, null, null), requestId);
        if (result.task().status() == WorkflowTaskStatus.APPROVED) quote.approve();
        return quoteResponse(quoteRepository.save(quote));
    }

    @Transactional
    public SalesQuoteResponse rejectQuote(FloworaPrincipal actor, String quoteId, String requestId) {
        SalesQuoteEntity quote = quoteRepository.findByIdAndOrganizationId(quoteId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("salesQuote", quoteId));
        if (quote.status() != SalesQuoteStatus.SUBMITTED || quote.workflowTaskId() == null) throw new IllegalStateException("Sales quote is not awaiting approval");
        workflowService.act(actor, quote.workflowTaskId(), new ActionRequest(WorkflowAction.REJECT, null, null), requestId);
        quote.reject();
        return quoteResponse(quoteRepository.save(quote));
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> orders(String organizationId, String query, Pageable pageable) {
        Page<SalesOrderEntity> page = orderRepository.search(organizationId, clean(query), pageable);
        return PageResponse.from(page.map(this::orderResponse));
    }

    @Transactional
    public SalesOrderResponse createOrder(FloworaPrincipal actor, SalesOrderCreate body) {
        CustomerEntity customer = requireCustomer(actor.organizationId(), body.customerId());
        requireWarehouse(actor.organizationId(), body.warehouseId());
        requireItem(actor.organizationId(), body.itemId());
        SalesQuoteEntity quote = null;
        if (body.quoteId() != null && !body.quoteId().isBlank()) {
            quote = quoteRepository.findByIdAndOrganizationId(body.quoteId(), actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("salesQuote", body.quoteId()));
            if (quote.status() != SalesQuoteStatus.APPROVED) throw new IllegalStateException("Only an approved quote can become a sales order");
            SalesQuoteLineEntity quoteLine = quoteLineRepository.findFirstByOrganizationIdAndQuoteId(actor.organizationId(), quote.id()).orElseThrow();
            if (!quote.customerId().equals(body.customerId()) || !quoteLine.itemId().equals(body.itemId())) throw new IllegalArgumentException("Sales order does not match its quote");
        }
        BigDecimal total = SalesQuoteLineEntity.total(body.quantity(), body.unitPrice(), body.discountRate(), body.taxRate());
        LocalDate dueDate = body.dueDate() == null ? LocalDate.now().plusDays(customer.paymentTermsDays()) : body.dueDate();
        SalesOrderEntity order = orderRepository.save(new SalesOrderEntity(
                actor.organizationId(), nextNumber("SO"), quote == null ? null : quote.id(), customer.id(), body.warehouseId(), SalesOrderStatus.CONFIRMED,
                clean(body.currencyCode()).toUpperCase(), LocalDate.now(), dueDate, total, clean(body.note()), actor.userId()
        ));
        orderLineRepository.save(new SalesOrderLineEntity(actor.organizationId(), order.id(), body.itemId(), body.quantity(), body.unitPrice(), body.discountRate(), body.taxRate()));
        receivableRepository.save(new ReceivableEntity(actor.organizationId(), nextNumber("AR"), order.id(), customer.id(), "SALES_ORDER", order.id(), order.currencyCode(), total, dueDate));
        accountingService.postSalesOrder(actor.organizationId(), actor.userId(), order.id(), total, order.currencyCode(), order.orderDate());
        if (quote != null) quote.markConverted();
        if (quote != null) quoteRepository.save(quote);
        return orderResponse(order);
    }

    @Transactional
    public DeliveryResponse deliver(FloworaPrincipal actor, DeliveryCreate body) {
        SalesOrderEntity order = orderRepository.findByIdAndOrganizationId(body.salesOrderId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("salesOrder", body.salesOrderId()));
        if (order.status() == SalesOrderStatus.CANCELLED || order.status() == SalesOrderStatus.FULFILLED) throw new IllegalStateException("Sales order is not open for fulfillment");
        if (!order.warehouseId().equals(body.warehouseId())) throw new IllegalArgumentException("Delivery warehouse must match the sales order warehouse");
        SalesOrderLineEntity line = orderLineRepository.findByIdAndOrganizationId(body.salesOrderLineId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("salesOrderLine", body.salesOrderLineId()));
        if (!line.salesOrderId().equals(order.id())) throw new IllegalArgumentException("Sales order line does not belong to the sales order");
        ItemEntity item = requireItem(actor.organizationId(), line.itemId());
        line.fulfill(body.quantity());
        DeliveryEntity delivery = deliveryRepository.save(new DeliveryEntity(actor.organizationId(), nextNumber("DO"), order.id(), order.warehouseId(), actor.userId()));
        BigDecimal unitCost = item.inventoryManaged() ? inventoryService.issueForSales(actor, order.warehouseId(), item.id(), body.quantity(), delivery.id()) : item.averageCost();
        deliveryLineRepository.save(new DeliveryLineEntity(actor.organizationId(), delivery.id(), line.id(), line.itemId(), body.quantity(), unitCost));
        accountingService.postSalesDelivery(actor.organizationId(), actor.userId(), delivery.id(), body.quantity().multiply(unitCost), order.currencyCode(), LocalDate.now());
        orderLineRepository.save(line);
        order.updateFulfillment(line.remainingQuantity().signum() == 0, line.fulfilledQuantity().signum() > 0);
        orderRepository.save(order);
        return new DeliveryResponse(delivery.id(), delivery.number(), delivery.salesOrderId(), delivery.warehouseId(), line.itemId(), body.quantity(), unitCost, delivery.status(), delivery.postedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> deliveries(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(deliveryRepository.search(organizationId, clean(query), pageable).map(this::deliveryResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceivableResponse> receivables(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(receivableRepository.search(organizationId, clean(query), pageable).map(this::receivableResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> payments(String organizationId, String receivableId, Pageable pageable) {
        return PageResponse.from(paymentRepository.findByOrganizationIdAndReceivableIdOrderByPaymentDateDesc(organizationId, clean(receivableId), pageable).map(this::paymentResponse));
    }

    @Transactional
    public PaymentResponse pay(FloworaPrincipal actor, PaymentCreate body) {
        ReceivableEntity receivable = receivableRepository.findByIdAndOrganizationId(body.receivableId(), actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("receivable", body.receivableId()));
        receivable.recordPayment(body.amount());
        PaymentEntity payment = paymentRepository.save(new PaymentEntity(
                actor.organizationId(), nextNumber("PM"), receivable.id(), receivable.customerId(), body.amount(), receivable.currencyCode(), body.method(), body.paymentDate(), clean(body.reference()), actor.userId()
        ));
        receivableRepository.save(receivable);
        accountingService.postCustomerPayment(actor.organizationId(), actor.userId(), payment.id(), payment.amount(), payment.currencyCode(), payment.paymentDate());
        return paymentResponse(payment);
    }

    private SalesQuoteResponse quoteResponse(SalesQuoteEntity quote) {
        SalesQuoteLineEntity line = quoteLineRepository.findFirstByOrganizationIdAndQuoteId(quote.organizationId(), quote.id()).orElseThrow();
        return new SalesQuoteResponse(quote.id(), quote.number(), quote.status(), quote.workflowTaskId(), quote.customerId(), line.itemId(), line.quantity(), line.unitPrice(), line.discountRate(), line.taxRate(), quote.currencyCode(), quote.validUntil(), quote.totalAmount(), quote.note(), quote.approvedAt());
    }

    private SalesOrderResponse orderResponse(SalesOrderEntity order) {
        SalesOrderLineEntity line = orderLineRepository.findFirstByOrganizationIdAndSalesOrderId(order.organizationId(), order.id()).orElseThrow();
        ReceivableEntity receivable = receivableRepository.findFirstByOrganizationIdAndSalesOrderId(order.organizationId(), order.id()).orElse(null);
        BigDecimal receivableAmount = receivable == null ? BigDecimal.ZERO : receivable.totalAmount();
        BigDecimal paidAmount = receivable == null ? BigDecimal.ZERO : receivable.paidAmount();
        return new SalesOrderResponse(order.id(), order.number(), order.status(), order.quoteId(), order.customerId(), order.warehouseId(), line.id(), line.itemId(), line.orderedQuantity(), line.fulfilledQuantity(), line.remainingQuantity(), line.unitPrice(), line.discountRate(), line.taxRate(), order.currencyCode(), order.orderDate(), order.dueDate(), order.totalAmount(), receivableAmount, paidAmount, receivable == null ? BigDecimal.ZERO : receivable.remainingAmount(), order.note());
    }

    private DeliveryResponse deliveryResponse(DeliveryEntity delivery) {
        DeliveryLineEntity line = deliveryLineRepository.findFirstByOrganizationIdAndDeliveryId(delivery.organizationId(), delivery.id()).orElseThrow();
        return new DeliveryResponse(delivery.id(), delivery.number(), delivery.salesOrderId(), delivery.warehouseId(), line.itemId(), line.quantity(), line.unitCost(), delivery.status(), delivery.postedAt());
    }

    private ReceivableResponse receivableResponse(ReceivableEntity entity) {
        return new ReceivableResponse(entity.id(), entity.number(), entity.salesOrderId(), entity.customerId(), entity.sourceType(), entity.currencyCode(), entity.totalAmount(), entity.paidAmount(), entity.remainingAmount(), entity.status(), entity.dueDate());
    }

    private PaymentResponse paymentResponse(PaymentEntity entity) {
        return new PaymentResponse(entity.id(), entity.number(), entity.receivableId(), entity.customerId(), entity.amount(), entity.currencyCode(), entity.method(), entity.paymentDate(), entity.reference());
    }

    private CustomerEntity requireCustomer(String organizationId, String id) {
        return customerRepository.findByIdAndOrganizationId(clean(id), organizationId).filter(CustomerEntity::active).orElseThrow(() -> new ResourceNotFoundException("customer", id));
    }

    private ItemEntity requireItem(String organizationId, String id) {
        return itemRepository.findByIdAndOrganizationId(clean(id), organizationId).filter(ItemEntity::active).orElseThrow(() -> new ResourceNotFoundException("item", id));
    }

    private void requireWarehouse(String organizationId, String id) {
        warehouseRepository.findByIdAndOrganizationId(clean(id), organizationId).filter(entity -> entity.active()).orElseThrow(() -> new ResourceNotFoundException("warehouse", id));
    }

    private String nextNumber(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
