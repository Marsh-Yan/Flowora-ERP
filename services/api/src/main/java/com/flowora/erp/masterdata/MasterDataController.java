package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.MasterDataDtos.AccountRequest;
import com.flowora.erp.masterdata.MasterDataDtos.AccountResponse;
import com.flowora.erp.masterdata.MasterDataDtos.CurrencyRequest;
import com.flowora.erp.masterdata.MasterDataDtos.CurrencyResponse;
import com.flowora.erp.masterdata.MasterDataDtos.CustomerRequest;
import com.flowora.erp.masterdata.MasterDataDtos.CustomerResponse;
import com.flowora.erp.masterdata.MasterDataDtos.ExchangeRateRequest;
import com.flowora.erp.masterdata.MasterDataDtos.ExchangeRateResponse;
import com.flowora.erp.masterdata.MasterDataDtos.ImportResult;
import com.flowora.erp.masterdata.MasterDataDtos.ItemRequest;
import com.flowora.erp.masterdata.MasterDataDtos.ItemResponse;
import com.flowora.erp.masterdata.MasterDataDtos.SupplierRequest;
import com.flowora.erp.masterdata.MasterDataDtos.SupplierResponse;
import com.flowora.erp.masterdata.MasterDataDtos.TaxRateRequest;
import com.flowora.erp.masterdata.MasterDataDtos.TaxRateResponse;
import com.flowora.erp.masterdata.MasterDataDtos.WarehouseRequest;
import com.flowora.erp.masterdata.MasterDataDtos.WarehouseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/masters")
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    public ApiResponse<PageResponse<CustomerResponse>> customers(
            @RequestParam(defaultValue = "") String query,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(service.customers(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createCustomer(organizationId(authentication), body), request);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<CustomerResponse> updateCustomer(@PathVariable String id, @Valid @RequestBody CustomerRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateCustomer(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<Void> deactivateCustomer(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateCustomer(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/suppliers")
    public ApiResponse<PageResponse<SupplierResponse>> suppliers(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.suppliers(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createSupplier(organizationId(authentication), body), request);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<SupplierResponse> updateSupplier(@PathVariable String id, @Valid @RequestBody SupplierRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateSupplier(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/suppliers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<Void> deactivateSupplier(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateSupplier(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/items")
    public ApiResponse<PageResponse<ItemResponse>> items(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.items(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<ItemResponse> createItem(@Valid @RequestBody ItemRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createItem(organizationId(authentication), body), request);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<ItemResponse> updateItem(@PathVariable String id, @Valid @RequestBody ItemRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateItem(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS')")
    public ApiResponse<Void> deactivateItem(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateItem(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/warehouses")
    public ApiResponse<PageResponse<WarehouseResponse>> warehouses(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.warehouses(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createWarehouse(organizationId(authentication), body), request);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<WarehouseResponse> updateWarehouse(@PathVariable String id, @Valid @RequestBody WarehouseRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateWarehouse(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public ApiResponse<Void> deactivateWarehouse(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateWarehouse(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/currencies")
    public ApiResponse<PageResponse<CurrencyResponse>> currencies(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.currencies(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/currencies")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<CurrencyResponse> createCurrency(@Valid @RequestBody CurrencyRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createCurrency(organizationId(authentication), body), request);
    }

    @PutMapping("/currencies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<CurrencyResponse> updateCurrency(@PathVariable String id, @Valid @RequestBody CurrencyRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateCurrency(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/currencies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<Void> deactivateCurrency(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateCurrency(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/exchange-rates")
    public ApiResponse<PageResponse<ExchangeRateResponse>> exchangeRates(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.exchangeRates(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/exchange-rates")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<ExchangeRateResponse> createExchangeRate(@Valid @RequestBody ExchangeRateRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createExchangeRate(organizationId(authentication), body), request);
    }

    @PutMapping("/exchange-rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<ExchangeRateResponse> updateExchangeRate(@PathVariable String id, @Valid @RequestBody ExchangeRateRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateExchangeRate(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/exchange-rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<Void> deactivateExchangeRate(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateExchangeRate(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/tax-rates")
    public ApiResponse<PageResponse<TaxRateResponse>> taxRates(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.taxRates(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/tax-rates")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<TaxRateResponse> createTaxRate(@Valid @RequestBody TaxRateRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createTaxRate(organizationId(authentication), body), request);
    }

    @PutMapping("/tax-rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<TaxRateResponse> updateTaxRate(@PathVariable String id, @Valid @RequestBody TaxRateRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateTaxRate(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/tax-rates/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<Void> deactivateTaxRate(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateTaxRate(organizationId(authentication), id);
        return response(null, request);
    }

    @GetMapping("/accounts")
    public ApiResponse<PageResponse<AccountResponse>> accounts(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.accounts(organizationId(authentication), query, pageable), request);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<AccountResponse> createAccount(@Valid @RequestBody AccountRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.createAccount(organizationId(authentication), body), request);
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<AccountResponse> updateAccount(@PathVariable String id, @Valid @RequestBody AccountRequest body, Authentication authentication, HttpServletRequest request) {
        return response(service.updateAccount(organizationId(authentication), id, body), request);
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<Void> deactivateAccount(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        service.deactivateAccount(organizationId(authentication), id);
        return response(null, request);
    }

    @PostMapping(value = "/{resource}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS', 'WAREHOUSE')")
    public ApiResponse<ImportResult> importResource(@PathVariable String resource, @RequestParam("file") MultipartFile file, Authentication authentication, HttpServletRequest request) {
        return response(service.importResource(organizationId(authentication), resource, file), request);
    }

    @GetMapping(value = "/{resource}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportResource(@PathVariable String resource, Authentication authentication, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(resource + ".csv", StandardCharsets.UTF_8).build());
        headers.set("X-Request-Id", RequestIdFilter.get(request));
        return ResponseEntity.ok().headers(headers).body(service.exportResource(organizationId(authentication), resource));
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }

    private String organizationId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal principal) {
            return principal.organizationId();
        }
        return "org-demo";
    }
}
