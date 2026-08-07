package com.flowora.erp.finance;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.finance.FinanceDtos.AgingRow;
import com.flowora.erp.finance.FinanceDtos.AccountingPeriodResponse;
import com.flowora.erp.finance.FinanceDtos.FinancialStatementResponse;
import com.flowora.erp.finance.FinanceDtos.JournalEntryResponse;
import com.flowora.erp.finance.FinanceDtos.ManualJournalCreate;
import com.flowora.erp.finance.FinanceDtos.PayableResponse;
import com.flowora.erp.finance.FinanceDtos.SupplierPaymentCreate;
import com.flowora.erp.finance.FinanceDtos.SupplierPaymentResponse;
import com.flowora.erp.finance.FinanceDtos.TrialBalanceResponse;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final AccountingService service;

    public FinanceController(AccountingService service) {
        this.service = service;
    }

    @GetMapping("/journals")
    public ApiResponse<PageResponse<JournalEntryResponse>> journals(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @PageableDefault(size = 30) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        return response(service.journals(principal(authentication).organizationId(), start, end, pageable), request);
    }

    @PostMapping("/journals/manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<JournalEntryResponse> manual(@Valid @RequestBody ManualJournalCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.manual(principal(authentication), body), request);
    }

    @GetMapping("/periods")
    public ApiResponse<PageResponse<AccountingPeriodResponse>> periods(@PageableDefault(size = 24) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.periods(principal(authentication).organizationId(), pageable), request);
    }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<AccountingPeriodResponse> closePeriod(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.closePeriod(principal(authentication), id), request);
    }

    @GetMapping("/reports/trial-balance")
    public ApiResponse<TrialBalanceResponse> trialBalance(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, Authentication authentication, HttpServletRequest request) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        return response(service.trialBalance(principal(authentication).organizationId(), start, end), request);
    }

    @GetMapping("/reports/income-statement")
    public ApiResponse<FinancialStatementResponse> incomeStatement(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, Authentication authentication, HttpServletRequest request) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        return response(service.incomeStatement(principal(authentication).organizationId(), start, end), request);
    }

    @GetMapping("/reports/balance-sheet")
    public ApiResponse<FinancialStatementResponse> balanceSheet(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, Authentication authentication, HttpServletRequest request) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? LocalDate.of(end.getYear(), 1, 1) : from;
        return response(service.balanceSheet(principal(authentication).organizationId(), start, end), request);
    }

    @GetMapping("/payables")
    public ApiResponse<PageResponse<PayableResponse>> payables(@RequestParam(defaultValue = "") String query, @PageableDefault(size = 30) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.payables(principal(authentication).organizationId(), query, pageable), request);
    }

    @GetMapping("/payables/{id}/payments")
    public ApiResponse<PageResponse<SupplierPaymentResponse>> supplierPayments(@PathVariable String id, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.supplierPayments(principal(authentication).organizationId(), id, pageable), request);
    }

    @PostMapping("/supplier-payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<SupplierPaymentResponse> supplierPayment(@Valid @RequestBody SupplierPaymentCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.paySupplier(principal(authentication), body), request);
    }

    @GetMapping("/reports/aging/receivables")
    public ApiResponse<List<AgingRow>> receivableAging(@RequestParam(required = false) LocalDate asOf, Authentication authentication, HttpServletRequest request) {
        return response(service.receivableAging(principal(authentication).organizationId(), asOf == null ? LocalDate.now() : asOf), request);
    }

    @GetMapping("/reports/aging/payables")
    public ApiResponse<List<AgingRow>> payableAging(@RequestParam(required = false) LocalDate asOf, Authentication authentication, HttpServletRequest request) {
        return response(service.payableAging(principal(authentication).organizationId(), asOf == null ? LocalDate.now() : asOf), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) return current;
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
