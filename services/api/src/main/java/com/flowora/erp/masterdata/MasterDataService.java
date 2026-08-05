package com.flowora.erp.masterdata;

import com.flowora.erp.common.api.MasterDataConflictException;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
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
import com.flowora.erp.masterdata.MasterDataDtos.OrganizationSettingsRequest;
import com.flowora.erp.masterdata.MasterDataDtos.OrganizationSettingsResponse;
import com.flowora.erp.masterdata.MasterDataDtos.SupplierRequest;
import com.flowora.erp.masterdata.MasterDataDtos.SupplierResponse;
import com.flowora.erp.masterdata.MasterDataDtos.TaxRateRequest;
import com.flowora.erp.masterdata.MasterDataDtos.TaxRateResponse;
import com.flowora.erp.masterdata.MasterDataDtos.WarehouseRequest;
import com.flowora.erp.masterdata.MasterDataDtos.WarehouseResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MasterDataService {
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final TaxRateRepository taxRateRepository;
    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;

    public MasterDataService(
            CustomerRepository customerRepository,
            SupplierRepository supplierRepository,
            ItemRepository itemRepository,
            WarehouseRepository warehouseRepository,
            CurrencyRepository currencyRepository,
            ExchangeRateRepository exchangeRateRepository,
            TaxRateRepository taxRateRepository,
            AccountRepository accountRepository,
            OrganizationRepository organizationRepository
    ) {
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.taxRateRepository = taxRateRepository;
        this.accountRepository = accountRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> customers(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(customerRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::customerResponse));
    }

    @Transactional
    public CustomerResponse createCustomer(String organizationId, CustomerRequest request) {
        String code = code(request.code());
        ensureNewCode(customerRepository.existsByOrganizationIdAndCode(organizationId, code), "customer", code);
        return customerResponse(customerRepository.save(new CustomerEntity(
                organizationId, code, text(request.name()), text(request.contactName()), text(request.email()),
                text(request.phone()), text(request.address()), code(request.currencyCode()), request.paymentTermsDays(), request.active()
        )));
    }

    @Transactional
    public CustomerResponse updateCustomer(String organizationId, String id, CustomerRequest request) {
        CustomerEntity entity = customerRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("customer", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && customerRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("customer", code);
        }
        entity.update(code, text(request.name()), text(request.contactName()), text(request.email()), text(request.phone()),
                text(request.address()), code(request.currencyCode()), request.paymentTermsDays(), request.active());
        return customerResponse(customerRepository.save(entity));
    }

    @Transactional
    public void deactivateCustomer(String organizationId, String id) {
        CustomerEntity entity = customerRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("customer", id));
        entity.update(entity.code(), entity.name(), entity.contactName(), entity.email(), entity.phone(), entity.address(),
                entity.currencyCode(), entity.paymentTermsDays(), false);
        customerRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> suppliers(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(supplierRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::supplierResponse));
    }

    @Transactional
    public SupplierResponse createSupplier(String organizationId, SupplierRequest request) {
        String code = code(request.code());
        ensureNewCode(supplierRepository.existsByOrganizationIdAndCode(organizationId, code), "supplier", code);
        return supplierResponse(supplierRepository.save(new SupplierEntity(
                organizationId, code, text(request.name()), text(request.contactName()), text(request.email()),
                text(request.phone()), text(request.address()), code(request.currencyCode()), request.paymentTermsDays(), request.active()
        )));
    }

    @Transactional
    public SupplierResponse updateSupplier(String organizationId, String id, SupplierRequest request) {
        SupplierEntity entity = supplierRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("supplier", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && supplierRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("supplier", code);
        }
        entity.update(code, text(request.name()), text(request.contactName()), text(request.email()), text(request.phone()),
                text(request.address()), code(request.currencyCode()), request.paymentTermsDays(), request.active());
        return supplierResponse(supplierRepository.save(entity));
    }

    @Transactional
    public void deactivateSupplier(String organizationId, String id) {
        SupplierEntity entity = supplierRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("supplier", id));
        entity.update(entity.code(), entity.name(), entity.contactName(), entity.email(), entity.phone(), entity.address(),
                entity.currencyCode(), entity.paymentTermsDays(), false);
        supplierRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> items(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(itemRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::itemResponse));
    }

    @Transactional
    public ItemResponse createItem(String organizationId, ItemRequest request) {
        String code = code(request.code());
        ensureNewCode(itemRepository.existsByOrganizationIdAndCode(organizationId, code), "item", code);
        return itemResponse(itemRepository.save(new ItemEntity(
                organizationId, code, text(request.name()), request.type(), text(request.unit()), request.salesPrice(),
                request.purchasePrice(), request.averageCost(), request.taxRate(), request.inventoryManaged(), request.active()
        )));
    }

    @Transactional
    public ItemResponse updateItem(String organizationId, String id, ItemRequest request) {
        ItemEntity entity = itemRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("item", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && itemRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("item", code);
        }
        entity.update(code, text(request.name()), request.type(), text(request.unit()), request.salesPrice(), request.purchasePrice(),
                request.averageCost(), request.taxRate(), request.inventoryManaged(), request.active());
        return itemResponse(itemRepository.save(entity));
    }

    @Transactional
    public void deactivateItem(String organizationId, String id) {
        ItemEntity entity = itemRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("item", id));
        entity.update(entity.code(), entity.name(), entity.type(), entity.unit(), entity.salesPrice(), entity.purchasePrice(),
                entity.averageCost(), entity.taxRate(), entity.inventoryManaged(), false);
        itemRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> warehouses(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(warehouseRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::warehouseResponse));
    }

    @Transactional
    public WarehouseResponse createWarehouse(String organizationId, WarehouseRequest request) {
        String code = code(request.code());
        ensureNewCode(warehouseRepository.existsByOrganizationIdAndCode(organizationId, code), "warehouse", code);
        return warehouseResponse(warehouseRepository.save(new WarehouseEntity(organizationId, code, text(request.name()), text(request.address()), request.active())));
    }

    @Transactional
    public WarehouseResponse updateWarehouse(String organizationId, String id, WarehouseRequest request) {
        WarehouseEntity entity = warehouseRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("warehouse", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && warehouseRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("warehouse", code);
        }
        entity.update(code, text(request.name()), text(request.address()), request.active());
        return warehouseResponse(warehouseRepository.save(entity));
    }

    @Transactional
    public void deactivateWarehouse(String organizationId, String id) {
        WarehouseEntity entity = warehouseRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("warehouse", id));
        entity.update(entity.code(), entity.name(), entity.address(), false);
        warehouseRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<CurrencyResponse> currencies(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(currencyRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::currencyResponse));
    }

    @Transactional
    public CurrencyResponse createCurrency(String organizationId, CurrencyRequest request) {
        String code = code(request.code());
        ensureNewCode(currencyRepository.existsByOrganizationIdAndCode(organizationId, code), "currency", code);
        return currencyResponse(currencyRepository.save(new CurrencyEntity(organizationId, code, text(request.name()), text(request.symbol()), request.decimalPlaces(), request.active())));
    }

    @Transactional
    public CurrencyResponse updateCurrency(String organizationId, String id, CurrencyRequest request) {
        CurrencyEntity entity = currencyRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("currency", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && currencyRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("currency", code);
        }
        entity.update(code, text(request.name()), text(request.symbol()), request.decimalPlaces(), request.active());
        return currencyResponse(currencyRepository.save(entity));
    }

    @Transactional
    public void deactivateCurrency(String organizationId, String id) {
        CurrencyEntity entity = currencyRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("currency", id));
        entity.update(entity.code(), entity.name(), entity.symbol(), entity.decimalPlaces(), false);
        currencyRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExchangeRateResponse> exchangeRates(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(exchangeRateRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::exchangeRateResponse));
    }

    @Transactional
    public ExchangeRateResponse createExchangeRate(String organizationId, ExchangeRateRequest request) {
        return exchangeRateResponse(exchangeRateRepository.save(new ExchangeRateEntity(
                organizationId, code(request.baseCurrencyCode()), code(request.quoteCurrencyCode()), request.rate(), request.effectiveDate(), request.active()
        )));
    }

    @Transactional
    public ExchangeRateResponse updateExchangeRate(String organizationId, String id, ExchangeRateRequest request) {
        ExchangeRateEntity entity = exchangeRateRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("exchangeRate", id));
        entity.update(code(request.baseCurrencyCode()), code(request.quoteCurrencyCode()), request.rate(), request.effectiveDate(), request.active());
        return exchangeRateResponse(exchangeRateRepository.save(entity));
    }

    @Transactional
    public void deactivateExchangeRate(String organizationId, String id) {
        ExchangeRateEntity entity = exchangeRateRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("exchangeRate", id));
        entity.update(entity.baseCurrencyCode(), entity.quoteCurrencyCode(), entity.rate(), entity.effectiveDate(), false);
        exchangeRateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxRateResponse> taxRates(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(taxRateRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::taxRateResponse));
    }

    @Transactional
    public TaxRateResponse createTaxRate(String organizationId, TaxRateRequest request) {
        String code = code(request.code());
        ensureNewCode(taxRateRepository.existsByOrganizationIdAndCode(organizationId, code), "taxRate", code);
        return taxRateResponse(taxRateRepository.save(new TaxRateEntity(organizationId, code, text(request.name()), request.rate(), request.exempt(), request.effectiveDate(), request.active())));
    }

    @Transactional
    public TaxRateResponse updateTaxRate(String organizationId, String id, TaxRateRequest request) {
        TaxRateEntity entity = taxRateRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("taxRate", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && taxRateRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("taxRate", code);
        }
        entity.update(code, text(request.name()), request.rate(), request.exempt(), request.effectiveDate(), request.active());
        return taxRateResponse(taxRateRepository.save(entity));
    }

    @Transactional
    public void deactivateTaxRate(String organizationId, String id) {
        TaxRateEntity entity = taxRateRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("taxRate", id));
        entity.update(entity.code(), entity.name(), entity.rate(), entity.exempt(), entity.effectiveDate(), false);
        taxRateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> accounts(String organizationId, String query, Pageable pageable) {
        return PageResponse.from(accountRepository.search(organizationId, cleanQuery(query), safePageable(pageable)).map(this::accountResponse));
    }

    @Transactional
    public AccountResponse createAccount(String organizationId, AccountRequest request) {
        String code = code(request.code());
        ensureNewCode(accountRepository.existsByOrganizationIdAndCode(organizationId, code), "account", code);
        return accountResponse(accountRepository.save(new AccountEntity(organizationId, code, text(request.name()), request.type(), text(request.parentCode()), request.postingAllowed(), request.active())));
    }

    @Transactional
    public AccountResponse updateAccount(String organizationId, String id, AccountRequest request) {
        AccountEntity entity = accountRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("account", id));
        String code = code(request.code());
        if (!entity.code().equalsIgnoreCase(code) && accountRepository.existsByOrganizationIdAndCode(organizationId, code)) {
            throw conflict("account", code);
        }
        entity.update(code, text(request.name()), request.type(), text(request.parentCode()), request.postingAllowed(), request.active());
        return accountResponse(accountRepository.save(entity));
    }

    @Transactional
    public void deactivateAccount(String organizationId, String id) {
        AccountEntity entity = accountRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> notFound("account", id));
        entity.update(entity.code(), entity.name(), entity.type(), entity.parentCode(), entity.postingAllowed(), false);
        accountRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse organizationSettings(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(this::organizationResponse)
                .orElseGet(() -> new OrganizationSettingsResponse(
                        organizationId, "Demo Organization", "USD", "UTC", new BigDecimal("10000.0000"), BigDecimal.ZERO, true
                ));
    }

    @Transactional
    public OrganizationSettingsResponse updateOrganizationSettings(String organizationId, OrganizationSettingsRequest request) {
        OrganizationEntity entity = organizationRepository.findById(organizationId)
                .orElseGet(() -> new OrganizationEntity(organizationId, request.name(), code(request.baseCurrencyCode()),
                        request.timezone(), request.approvalThreshold(), request.defaultTaxRate()));
        entity.update(text(request.name()), code(request.baseCurrencyCode()), text(request.timezone()), request.approvalThreshold(), request.defaultTaxRate());
        return organizationResponse(organizationRepository.save(entity));
    }

    @Transactional
    public ImportResult importResource(String organizationId, String resource, MultipartFile file) {
        List<List<String>> rows = CsvSupport.read(file);
        if (rows.isEmpty()) {
            return new ImportResult(0, 0, List.of());
        }
        List<String> headers = rows.getFirst().stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
        int imported = 0;
        int rejected = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            try {
                List<String> row = rows.get(index);
                switch (resource) {
                    case "customers" -> createCustomer(organizationId, customerFromCsv(row, headers));
                    case "suppliers" -> createSupplier(organizationId, supplierFromCsv(row, headers));
                    case "items" -> createItem(organizationId, itemFromCsv(row, headers));
                    case "warehouses" -> createWarehouse(organizationId, warehouseFromCsv(row, headers));
                    default -> throw new IllegalArgumentException("Unsupported import resource: " + resource);
                }
                imported++;
            } catch (RuntimeException exception) {
                rejected++;
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("row", index + 1);
                error.put("message", exception.getMessage());
                errors.add(error);
            }
        }
        return new ImportResult(imported, rejected, errors);
    }

    @Transactional(readOnly = true)
    public String exportResource(String organizationId, String resource) {
        return switch (resource) {
            case "customers" -> exportCustomers(organizationId);
            case "suppliers" -> exportSuppliers(organizationId);
            case "items" -> exportItems(organizationId);
            case "warehouses" -> exportWarehouses(organizationId);
            case "currencies" -> exportCurrencies(organizationId);
            case "exchange-rates" -> exportExchangeRates(organizationId);
            case "tax-rates" -> exportTaxRates(organizationId);
            case "accounts" -> exportAccounts(organizationId);
            default -> throw new IllegalArgumentException("Unsupported export resource: " + resource);
        };
    }

    private String exportCustomers(String organizationId) {
        List<CustomerResponse> rows = customers(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,contactName,email,phone,address,currencyCode,paymentTermsDays,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.contactName(), row.email(), row.phone(), row.address(), row.currencyCode(), row.paymentTermsDays(), row.active()))
                .toList());
    }

    private String exportSuppliers(String organizationId) {
        List<SupplierResponse> rows = suppliers(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,contactName,email,phone,address,currencyCode,paymentTermsDays,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.contactName(), row.email(), row.phone(), row.address(), row.currencyCode(), row.paymentTermsDays(), row.active()))
                .toList());
    }

    private String exportItems(String organizationId) {
        List<ItemResponse> rows = items(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,type,unit,salesPrice,purchasePrice,averageCost,taxRate,inventoryManaged,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.type(), row.unit(), row.salesPrice(), row.purchasePrice(), row.averageCost(), row.taxRate(), row.inventoryManaged(), row.active()))
                .toList());
    }

    private String exportWarehouses(String organizationId) {
        List<WarehouseResponse> rows = warehouses(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,address,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.address(), row.active()))
                .toList());
    }

    private String exportCurrencies(String organizationId) {
        List<CurrencyResponse> rows = currencies(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,symbol,decimalPlaces,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.symbol(), row.decimalPlaces(), row.active()))
                .toList());
    }

    private String exportTaxRates(String organizationId) {
        List<TaxRateResponse> rows = taxRates(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,rate,exempt,effectiveDate,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.rate(), row.exempt(), row.effectiveDate(), row.active()))
                .toList());
    }

    private String exportExchangeRates(String organizationId) {
        List<ExchangeRateResponse> rows = exchangeRates(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("baseCurrencyCode,quoteCurrencyCode,rate,effectiveDate,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.baseCurrencyCode(), row.quoteCurrencyCode(), row.rate(), row.effectiveDate(), row.active()))
                .toList());
    }

    private String exportAccounts(String organizationId) {
        List<AccountResponse> rows = accounts(organizationId, "", PageRequest.of(0, MAX_PAGE_SIZE)).content();
        return csv("code,name,type,parentCode,postingAllowed,active\n", rows.stream()
                .map(row -> CsvSupport.line(row.code(), row.name(), row.type(), row.parentCode(), row.postingAllowed(), row.active()))
                .toList());
    }

    private String csv(String header, List<String> rows) {
        return header + String.join("\n", rows) + (rows.isEmpty() ? "" : "\n");
    }

    private CustomerRequest customerFromCsv(List<String> row, List<String> headers) {
        return new CustomerRequest(requiredCsv(row, headers, "code"), requiredCsv(row, headers, "name"),
                CsvSupport.value(row, headers, "contactname"), CsvSupport.value(row, headers, "email"), CsvSupport.value(row, headers, "phone"),
                CsvSupport.value(row, headers, "address"), defaultCurrency(row, headers), integer(row, headers, "paymenttermsdays", 0),
                bool(row, headers, "active", true));
    }

    private SupplierRequest supplierFromCsv(List<String> row, List<String> headers) {
        return new SupplierRequest(requiredCsv(row, headers, "code"), requiredCsv(row, headers, "name"),
                CsvSupport.value(row, headers, "contactname"), CsvSupport.value(row, headers, "email"), CsvSupport.value(row, headers, "phone"),
                CsvSupport.value(row, headers, "address"), defaultCurrency(row, headers), integer(row, headers, "paymenttermsdays", 0),
                bool(row, headers, "active", true));
    }

    private ItemRequest itemFromCsv(List<String> row, List<String> headers) {
        return new ItemRequest(requiredCsv(row, headers, "code"), requiredCsv(row, headers, "name"),
                ItemType.valueOf(requiredCsv(row, headers, "type").toUpperCase(Locale.ROOT)), requiredCsv(row, headers, "unit"),
                decimal(row, headers, "salesprice", BigDecimal.ZERO), decimal(row, headers, "purchaseprice", BigDecimal.ZERO),
                decimal(row, headers, "averagecost", BigDecimal.ZERO), decimal(row, headers, "taxrate", BigDecimal.ZERO),
                bool(row, headers, "inventorymanaged", false), bool(row, headers, "active", true));
    }

    private WarehouseRequest warehouseFromCsv(List<String> row, List<String> headers) {
        return new WarehouseRequest(requiredCsv(row, headers, "code"), requiredCsv(row, headers, "name"),
                CsvSupport.value(row, headers, "address"), bool(row, headers, "active", true));
    }

    private String defaultCurrency(List<String> row, List<String> headers) {
        String value = CsvSupport.value(row, headers, "currencycode");
        return value.isBlank() ? "USD" : value;
    }

    private String requiredCsv(List<String> row, List<String> headers, String header) {
        String value = CsvSupport.value(row, headers, header);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + header);
        }
        return value;
    }

    private BigDecimal decimal(List<String> row, List<String> headers, String header, BigDecimal fallback) {
        String value = CsvSupport.value(row, headers, header);
        return value.isBlank() ? fallback : new BigDecimal(value);
    }

    private int integer(List<String> row, List<String> headers, String header, int fallback) {
        String value = CsvSupport.value(row, headers, header);
        return value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private boolean bool(List<String> row, List<String> headers, String header, boolean fallback) {
        String value = CsvSupport.value(row, headers, header);
        return value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private Pageable safePageable(Pageable pageable) {
        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }

    private String cleanQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private String code(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private void ensureNewCode(boolean exists, String resource, String code) {
        if (exists) {
            throw conflict(resource, code);
        }
    }

    private MasterDataConflictException conflict(String resource, String code) {
        return new MasterDataConflictException(resource, code);
    }

    private ResourceNotFoundException notFound(String resource, String id) {
        return new ResourceNotFoundException(resource, id);
    }

    private CustomerResponse customerResponse(CustomerEntity entity) {
        return new CustomerResponse(entity.id(), entity.code(), entity.name(), entity.contactName(), entity.email(), entity.phone(), entity.address(), entity.currencyCode(), entity.paymentTermsDays(), entity.active());
    }

    private SupplierResponse supplierResponse(SupplierEntity entity) {
        return new SupplierResponse(entity.id(), entity.code(), entity.name(), entity.contactName(), entity.email(), entity.phone(), entity.address(), entity.currencyCode(), entity.paymentTermsDays(), entity.active());
    }

    private ItemResponse itemResponse(ItemEntity entity) {
        return new ItemResponse(entity.id(), entity.code(), entity.name(), entity.type(), entity.unit(), entity.salesPrice(), entity.purchasePrice(), entity.averageCost(), entity.taxRate(), entity.inventoryManaged(), entity.active());
    }

    private WarehouseResponse warehouseResponse(WarehouseEntity entity) {
        return new WarehouseResponse(entity.id(), entity.code(), entity.name(), entity.address(), entity.active());
    }

    private CurrencyResponse currencyResponse(CurrencyEntity entity) {
        return new CurrencyResponse(entity.id(), entity.code(), entity.name(), entity.symbol(), entity.decimalPlaces(), entity.active());
    }

    private ExchangeRateResponse exchangeRateResponse(ExchangeRateEntity entity) {
        return new ExchangeRateResponse(entity.id(), entity.baseCurrencyCode(), entity.quoteCurrencyCode(), entity.rate(), entity.effectiveDate(), entity.active());
    }

    private TaxRateResponse taxRateResponse(TaxRateEntity entity) {
        return new TaxRateResponse(entity.id(), entity.code(), entity.name(), entity.rate(), entity.exempt(), entity.effectiveDate(), entity.active());
    }

    private AccountResponse accountResponse(AccountEntity entity) {
        return new AccountResponse(entity.id(), entity.code(), entity.name(), entity.type(), entity.parentCode(), entity.postingAllowed(), entity.active());
    }

    private OrganizationSettingsResponse organizationResponse(OrganizationEntity entity) {
        return new OrganizationSettingsResponse(entity.id(), entity.name(), entity.baseCurrencyCode(), entity.timezone(), entity.approvalThreshold(), entity.defaultTaxRate(), entity.active());
    }
}
