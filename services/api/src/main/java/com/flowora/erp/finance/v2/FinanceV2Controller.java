package com.flowora.erp.finance.v2;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.RequestIdFilter;
import com.flowora.erp.finance.v2.FinanceV2Dtos.*;
import com.flowora.erp.identity.FloworaAuthorization;
import com.flowora.erp.identity.FloworaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v2/finance")
public class FinanceV2Controller {
    private final FinanceDocumentService documents;
    private final FinanceLedgerService ledger;
    private final FinanceOperationsService operations;
    private final FloworaAuthorization authorization;

    public FinanceV2Controller(FinanceDocumentService documents, FinanceLedgerService ledger,
                               FinanceOperationsService operations, FloworaAuthorization authorization) {
        this.documents = documents;
        this.ledger = ledger;
        this.operations = operations;
        this.authorization = authorization;
    }

    @PostMapping("/invoices")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:invoice')")
    public ApiResponse<InvoiceView> createInvoice(@RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody InvoiceCreate body,
                                                   Authentication authentication, HttpServletRequest request) {
        return response(documents.createInvoice(principal(authentication), key, body), request);
    }

    @GetMapping("/invoice-stock-sources")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view') && @floworaAuthorization.hasOrganizationPermission(authentication, 'finance:invoice')")
    public ApiResponse<List<StockInvoiceSourceView>> stockInvoiceSources(Authentication authentication, HttpServletRequest request) {
        return response(documents.stockInvoiceSources(principal(authentication).organizationId()), request);
    }

    @GetMapping("/invoices")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<InvoiceView>> invoices(@RequestParam(defaultValue = "") String documentType,
                                                    @RequestParam(defaultValue = "") String status,
                                                    Authentication authentication, HttpServletRequest request) {
        return response(documents.invoices(principal(authentication).organizationId(), documentType, status), request);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<InvoiceView> invoice(@PathVariable String id, Authentication authentication,
                                             HttpServletRequest request) {
        return response(documents.invoice(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/invoices/{id}/match-exception/approve")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:match-exception')")
    public ApiResponse<InvoiceView> approveException(@PathVariable String id,
                                                      @Valid @RequestBody MatchExceptionApproval body,
                                                      Authentication authentication, HttpServletRequest request) {
        return response(documents.approveMatchException(principal(authentication), id, body.reason()), request);
    }

    @PostMapping("/invoices/{id}/post")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:post')")
    public ApiResponse<InvoiceView> postInvoice(@PathVariable String id, @RequestParam long version,
                                                 Authentication authentication, HttpServletRequest request) {
        return response(documents.postInvoice(principal(authentication), id, version), request);
    }

    @PostMapping("/payments")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:create')")
    public ApiResponse<PaymentView> createPayment(@RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody PaymentCreate body,
                                                   Authentication authentication, HttpServletRequest request) {
        return response(documents.createPayment(principal(authentication), key, body), request);
    }

    @GetMapping("/payments")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<PaymentView>> payments(@RequestParam(defaultValue = "") String status,
                                                    Authentication authentication, HttpServletRequest request) {
        return response(documents.payments(principal(authentication).organizationId(), status), request);
    }

    @PostMapping("/payments/{id}/post")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:post')")
    public ApiResponse<PaymentView> postPayment(@PathVariable String id, @RequestParam long version,
                                                 Authentication authentication, HttpServletRequest request) {
        return response(documents.postPayment(principal(authentication), id, version), request);
    }

    @PostMapping("/payments/{id}/allocations")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:allocate')")
    public ApiResponse<AllocationView> allocate(@PathVariable String id,
                                                 @Valid @RequestBody AllocationCreate body,
                                                 Authentication authentication, HttpServletRequest request) {
        return response(documents.allocate(principal(authentication), id, body), request);
    }

    @PostMapping("/allocations/{id}/reverse")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:allocate')")
    public ApiResponse<AllocationView> reverseAllocation(@PathVariable String id,
                                                          @RequestHeader("Idempotency-Key") String key,
                                                          @Valid @RequestBody AllocationReverse body,
                                                          Authentication authentication, HttpServletRequest request) {
        return response(documents.reverseAllocation(principal(authentication), id, key, body), request);
    }

    @GetMapping("/journals")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<JournalView>> journals(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                    Authentication authentication, HttpServletRequest request) {
        return response(ledger.journals(principal(authentication).organizationId(), from, to), request);
    }

    @PostMapping("/journals/{id}/reverse")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:post')")
    public ApiResponse<JournalView> reverseJournal(@PathVariable String id,
                                                   @RequestParam LocalDate accountingDate,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody PeriodAction body,
                                                   Authentication authentication, HttpServletRequest request) {
        FloworaPrincipal actor = principal(authentication);
        return response(ledger.reverse(actor.organizationId(), actor.userId(), id, accountingDate,
                body.reason(), key), request);
    }

    @PostMapping("/periods/{id}/close")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:post')")
    public ApiResponse<PeriodCloseView> closePeriod(@PathVariable String id,
                                                     @RequestHeader("Idempotency-Key") String key,
                                                     @Valid @RequestBody PeriodAction body,
                                                     Authentication authentication, HttpServletRequest request) {
        return response(operations.closePeriod(principal(authentication), id, body.reason(), key), request);
    }

    @PostMapping("/periods/{id}/reopen")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:period-reopen')")
    public ApiResponse<PeriodCloseView> reopenPeriod(@PathVariable String id,
                                                      @RequestHeader("Idempotency-Key") String key,
                                                      @Valid @RequestBody PeriodAction body,
                                                      Authentication authentication, HttpServletRequest request) {
        return response(operations.reopenPeriod(principal(authentication), id, body.reason(), key), request);
    }

    @GetMapping("/bank/accounts")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<BankAccountView>> bankAccounts(Authentication authentication, HttpServletRequest request) {
        return response(operations.bankAccounts(principal(authentication).organizationId()), request);
    }

    @PostMapping("/bank/statements/import")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:bank')")
    public ApiResponse<List<StatementLineView>> importStatements(@Valid @RequestBody StatementImport body,
                                                                  Authentication authentication, HttpServletRequest request) {
        return response(operations.importStatements(principal(authentication), body), request);
    }

    @GetMapping("/bank/statements")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<StatementLineView>> statements(@RequestParam(defaultValue = "") String bankAccountId,
                                                            @RequestParam(defaultValue = "") String status,
                                                            Authentication authentication, HttpServletRequest request) {
        return response(operations.statements(principal(authentication).organizationId(), bankAccountId, status), request);
    }

    @PostMapping("/bank/reconciliations")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:bank')")
    public ApiResponse<ReconciliationView> reconcile(@RequestHeader("Idempotency-Key") String key,
                                                      @Valid @RequestBody ReconciliationCreate body,
                                                      Authentication authentication, HttpServletRequest request) {
        return response(operations.reconcile(principal(authentication), key, body), request);
    }

    @PostMapping("/bank/reconciliations/{id}/reverse")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:bank')")
    public ApiResponse<ReconciliationView> reverseReconciliation(@PathVariable String id,
                                                                  @Valid @RequestBody PeriodAction body,
                                                                  Authentication authentication, HttpServletRequest request) {
        return response(operations.reverseReconciliation(principal(authentication), id, body.reason()), request);
    }

    @GetMapping("/bank/reconciliations")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<ReconciliationView>> reconciliations(@RequestParam(defaultValue = "") String bankAccountId,
                                                                  Authentication authentication, HttpServletRequest request) {
        return response(operations.reconciliations(principal(authentication).organizationId(), bankAccountId), request);
    }

    @PostMapping("/budgets")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:budget')")
    public ApiResponse<String> createBudget(@Valid @RequestBody BudgetCreate body,
                                             Authentication authentication, HttpServletRequest request) {
        return response(operations.createBudget(principal(authentication), body), request);
    }

    @GetMapping("/reports/budget-execution")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<BudgetExecutionView>> budgetExecution(@RequestParam int fiscalYear,
                                                                   Authentication authentication, HttpServletRequest request) {
        return response(operations.budgetExecution(principal(authentication).organizationId(), fiscalYear), request);
    }

    @PostMapping("/revaluations/{id}/reverse")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:post')")
    public ApiResponse<JournalView> reverseRevaluation(@PathVariable String id, @RequestParam LocalDate accountingDate,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PeriodAction body,
            Authentication authentication, HttpServletRequest request) {
        return response(operations.reverseRevaluation(principal(authentication), id, accountingDate, body.reason(), key), request);
    }

    @PostMapping("/revaluations")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:budget')")
    public ApiResponse<RevaluationView> revalue(@RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody RevaluationCreate body,
                                                 Authentication authentication, HttpServletRequest request) {
        return response(operations.revalue(principal(authentication), key, body), request);
    }

    @GetMapping("/reports/trial-balance")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<List<TrialBalanceRow>> trialBalance(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                           Authentication authentication, HttpServletRequest request) {
        return response(ledger.trialBalance(principal(authentication).organizationId(), from, to), request);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@floworaAuthorization.hasOrganizationPermission(authentication, 'finance:view')")
    public ApiResponse<FinanceDashboard> dashboard(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                                    Authentication authentication, HttpServletRequest request) {
        return response(operations.dashboard(principal(authentication).organizationId(), from, to), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        return authorization.principal(authentication);
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
