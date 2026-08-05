package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.MasterDataConflictException;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.masterdata.MasterDataDtos.CustomerRequest;
import com.flowora.erp.masterdata.MasterDataDtos.CustomerResponse;
import com.flowora.erp.masterdata.MasterDataDtos.ImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private TaxRateRepository taxRateRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private MasterDataService service;

    @Test
    void scopesCustomerSearchToTheAuthenticatedOrganization() {
        CustomerEntity customer = new CustomerEntity("org-a", "C-001", "Acme", null, null, null, null, "USD", 30, true);
        when(customerRepository.search(eq("org-a"), eq("Acme"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1));

        PageResponse<CustomerResponse> result = service.customers("org-a", " Acme ", PageRequest.of(0, 10));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).extracting(CustomerResponse::code).containsExactly("C-001");
        verify(customerRepository).search(eq("org-a"), eq("Acme"), any(PageRequest.class));
    }

    @Test
    void normalizesCustomerCodeBeforePersisting() {
        when(customerRepository.existsByOrganizationIdAndCode("org-a", "C-001")).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse result = service.createCustomer("org-a", new CustomerRequest(
                " c-001 ", " Acme ", null, "contact@example.com", null, null, "usd", 30, true
        ));

        assertThat(result.code()).isEqualTo("C-001");
        assertThat(result.name()).isEqualTo("Acme");
        ArgumentCaptor<CustomerEntity> captor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().organizationId()).isEqualTo("org-a");
    }

    @Test
    void rejectsDuplicateCustomerCode() {
        when(customerRepository.existsByOrganizationIdAndCode("org-a", "C-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer("org-a", new CustomerRequest(
                "C-001", "Acme", null, null, null, null, "USD", 0, true
        ))).isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void importsValidCustomersAndReportsInvalidRows() {
        when(customerRepository.existsByOrganizationIdAndCode("org-a", "C-001")).thenReturn(false);
        when(customerRepository.existsByOrganizationIdAndCode("org-a", "C-002")).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile csv = new MockMultipartFile(
                "file",
                "customers.csv",
                "text/csv",
                ("code,name,currencyCode,paymentTermsDays,active\n" +
                        "C-001,Acme,USD,30,true\n" +
                        ",Missing code,USD,30,true\n" +
                        "C-002,Globex,USD,45,true\n").getBytes()
        );

        ImportResult result = service.importResource("org-a", "customers", csv);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().get("row")).isEqualTo(3);
    }
}
