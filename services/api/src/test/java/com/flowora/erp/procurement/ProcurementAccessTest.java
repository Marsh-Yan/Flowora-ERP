package com.flowora.erp.procurement;

import com.flowora.erp.identity.*;
import com.flowora.erp.masterdata.*;
import com.flowora.erp.trade.v2.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcurementAccessTest {
    final PurchaseRequestRepository requests=mock(PurchaseRequestRepository.class);
    final PurchaseOrderRepository orders=mock(PurchaseOrderRepository.class);
    final SupplierRepository suppliers=mock(SupplierRepository.class);
    final WarehouseRepository warehouses=mock(WarehouseRepository.class);
    final ItemRepository items=mock(ItemRepository.class);
    final ProcurementService service=new ProcurementService(requests,mock(PurchaseRequestLineRepository.class),orders,
            mock(PurchaseOrderLineRepository.class),suppliers,warehouses,items);
    FloworaPrincipal actor(DataScope scope,List<String> bits) {
        return new FloworaPrincipal("user","user","User","org","Org","membership",null,scope,List.of("CUSTOM"),bits,false);
    }
    @Test void linkedRequestRequiresSupportedReadScopeBeforeResourceLookups() {
        var body=new ProcurementDtos.PurchaseOrderCreate("source","supplier","warehouse","item",BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,null,null);
        for(var a:List.of(actor(DataScope.SELF,List.of("procurement:create","procurement:view")),actor(DataScope.ALL,List.of("procurement:create"))))
            assertThatThrownBy(()->service.createOrder(a,body)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(requests,orders,suppliers,warehouses,items);
    }
    @Test void cancellationRejectsOutsideScopeBeforeCanonicalStateChanges() {
        var scope=mock(OrderReadScope.class); var documents=mock(TradeDocumentService.class);
        service.setReadScope(scope); service.setCanonicalDocuments(documents);
        var a=actor(DataScope.SELF,List.of("procurement:view","procurement:submit"));
        doThrow(new AccessDeniedException("Outside scope")).when(scope).requirePurchase(a,"foreign");
        assertThatThrownBy(()->service.cancelOrder(a,"foreign")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(documents,orders);
    }
    @Test void cancellationWithoutViewCannotReachEvenTheScopeLookup() {
        var scope=mock(OrderReadScope.class); service.setReadScope(scope);
        assertThatThrownBy(()->service.cancelOrder(actor(DataScope.ALL,List.of("procurement:submit")),"id")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(scope,orders);
    }
    @Test void missingScopePolicyDoesNotWidenScopedCancellation() {
        assertThatThrownBy(()->service.cancelOrder(actor(DataScope.DEPARTMENT,List.of("procurement:view","procurement:submit")),"id")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orders);
    }
}
