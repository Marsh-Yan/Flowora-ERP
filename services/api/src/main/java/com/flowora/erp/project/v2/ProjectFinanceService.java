package com.flowora.erp.project.v2;

import com.flowora.erp.finance.v2.FinanceDocumentService;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceLineCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceSourceCreate;
import com.flowora.erp.finance.v2.FinanceV2Dtos.InvoiceView;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.project.v2.ProjectFinanceDtos.ApprovalAction;
import com.flowora.erp.project.v2.ProjectFinanceDtos.BillingBasisView;
import com.flowora.erp.project.v2.ProjectFinanceDtos.BillingConfiguration;
import com.flowora.erp.project.v2.ProjectFinanceDtos.MemberCreate;
import com.flowora.erp.project.v2.ProjectFinanceDtos.MemberView;
import com.flowora.erp.project.v2.ProjectFinanceDtos.ProjectInvoiceCreate;
import com.flowora.erp.project.v2.ProjectFinanceDtos.ProjectProfitView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.flowora.erp.finance.v2.FinanceLedgerService.conflict;
import static com.flowora.erp.finance.v2.FinanceLedgerService.notFound;

@Service
public class ProjectFinanceService {
    private static final Set<String> BILLING_MODES = Set.of("FIXED_PRICE", "MILESTONE", "TIME_MATERIAL");
    private final JdbcTemplate jdbc;
    private final FinanceDocumentService finance;
    private final com.flowora.erp.project.ProjectLifecycleService lifecycle;

    public ProjectFinanceService(JdbcTemplate jdbc, FinanceDocumentService finance, com.flowora.erp.project.ProjectLifecycleService lifecycle) {
        this.lifecycle=lifecycle;
        this.jdbc = jdbc;
        this.finance = finance;
    }

    @Transactional
    public BillingConfiguration configure(FloworaPrincipal actor, String projectId, BillingConfiguration body) {
        finance.lockProjectBilling(actor.organizationId());
        lifecycle.requireOpen(actor.organizationId(), projectId);
        String mode = body.billingMode().toUpperCase();
        lifecycle.requireMilestoneModeChange(actor.organizationId(),projectId,mode);
        if (!BILLING_MODES.contains(mode) || body.contractAmount().signum() < 0) {
            throw conflict("INVALID_PROJECT_BILLING_CONFIGURATION", Map.of());
        }
        jdbc.update("""
                UPDATE flowora_project SET billing_mode=?,contract_amount=?,department_id=?,version_no=version_no+1
                WHERE id=? AND organization_id=?
                """, mode, body.contractAmount(), clean(body.departmentId()), projectId, actor.organizationId());
        if ("FIXED_PRICE".equals(mode) && body.contractAmount().signum() > 0) {
            upsertBasis(actor.organizationId(), projectId, "FIXED_PRICE", projectId,
                    "Fixed-price contract", body.contractAmount(), project(projectId, actor.organizationId()).currencyCode());
        }
        return new BillingConfiguration(mode, body.contractAmount(), clean(body.departmentId()));
    }

    @Transactional
    public MemberView addMember(FloworaPrincipal actor, String projectId, MemberCreate body) {
        lifecycle.requireOpen(actor.organizationId(), projectId);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO flowora_project_member(id,organization_id,project_id,user_id,project_role,active)
                VALUES (?,?,?,?,?,TRUE)
                ON DUPLICATE KEY UPDATE project_role=VALUES(project_role),active=TRUE
                """, id, actor.organizationId(), projectId, body.userId(), body.projectRole().toUpperCase());
        return members(actor.organizationId(), projectId).stream().filter(item -> item.userId().equals(body.userId())).findFirst().orElseThrow();
    }

    public List<MemberView> members(String organizationId, String projectId) {
        requireProject(organizationId, projectId);
        return jdbc.query("""
                SELECT id,user_id,project_role,active FROM flowora_project_member
                WHERE organization_id=? AND project_id=? ORDER BY created_at
                """, (rs, row) -> new MemberView(rs.getString("id"), rs.getString("user_id"),
                rs.getString("project_role"), rs.getBoolean("active")), organizationId, projectId);
    }

    @Transactional
    public BillingBasisView approveTimesheet(FloworaPrincipal actor, String timesheetId, ApprovalAction body) {
        finance.lockProjectBilling(actor.organizationId());
        Timesheet value = timesheetForUpdate(actor.organizationId(), timesheetId);
        lifecycle.requireOpen(actor.organizationId(),value.projectId());
        if ("APPROVED".equals(value.approvalStatus())) throw conflict("TIMESHEET_ALREADY_APPROVED", Map.of());
        if (!body.approved()) {
            jdbc.update("UPDATE flowora_timesheet SET approval_status='REJECTED',lifecycle_status='DRAFT',version_no=version_no+1 WHERE id=?", timesheetId);
            return null;
        }
        jdbc.update("""
                UPDATE flowora_timesheet SET approval_status='APPROVED',lifecycle_status='CONFIRMED',approved_by=?,
                approved_at=CURRENT_TIMESTAMP,version_no=version_no+1 WHERE id=?
                """, actor.userId(), timesheetId);
        if (!value.billable() || value.billableAmount().signum() <= 0) return null;
        return upsertBasis(actor.organizationId(), value.projectId(), "TIMESHEET", timesheetId,
                "Approved timesheet " + timesheetId, value.billableAmount(), value.currencyCode());
    }

    @Transactional
    public BillingBasisView approveExpense(FloworaPrincipal actor, String expenseId, ApprovalAction body) {
        finance.lockProjectBilling(actor.organizationId());
        Expense value = expenseForUpdate(actor.organizationId(), expenseId);
        lifecycle.requireOpen(actor.organizationId(),value.projectId());
        if ("APPROVED".equals(value.approvalStatus())) throw conflict("EXPENSE_ALREADY_APPROVED", Map.of());
        if (!body.approved()) {
            jdbc.update("UPDATE flowora_project_expense SET approval_status='REJECTED',lifecycle_status='DRAFT',version_no=version_no+1 WHERE id=?", expenseId);
            return null;
        }
        jdbc.update("""
                UPDATE flowora_project_expense SET approval_status='APPROVED',lifecycle_status='CONFIRMED',approved_by=?,
                approved_at=CURRENT_TIMESTAMP,version_no=version_no+1 WHERE id=?
                """, actor.userId(), expenseId);
        if (!value.billable() || value.billableAmount().signum() <= 0) return null;
        return upsertBasis(actor.organizationId(), value.projectId(), "EXPENSE", expenseId,
                "Approved expense " + expenseId, value.billableAmount(), value.currencyCode());
    }

    @Transactional
    public BillingBasisView approveMilestone(FloworaPrincipal actor, String milestoneId, ProjectFinanceDtos.MilestoneApprovalAction body,String requestId) {
        finance.lockProjectBilling(actor.organizationId());
        Milestone value = milestoneForUpdate(actor.organizationId(), milestoneId);
        lifecycle.requireMilestoneBilling(actor.organizationId(),value.projectId());
        if("APPROVED".equals(value.approvalStatus()))throw conflict("MILESTONE_ALREADY_APPROVED",Map.of());
        if(body.version()==null || value.version()!=body.version())throw conflict("OPTIMISTIC_LOCK_CONFLICT",Map.of());
        if(body.approved()==null || body.reason()==null || body.reason().isBlank() || body.reason().length()>500)throw conflict("MILESTONE_REASON_REQUIRED",Map.of());
        if (!"COMPLETED".equals(value.status()) || !value.billable() || value.billingAmount().signum() <= 0)throw conflict("MILESTONE_NOT_BILLABLE", Map.of());
        lifecycle.requireMilestoneTasksDone(actor.organizationId(),value.projectId(),milestoneId);
        if(!jdbc.queryForList("SELECT id FROM flowora_project_billing_basis WHERE organization_id=? AND basis_type='MILESTONE' AND source_id=? FOR UPDATE",actor.organizationId(),milestoneId).isEmpty())throw conflict("MILESTONE_BILLING_STATE_CONFLICT",Map.of());
        String state=body.approved()?"APPROVED":"REJECTED";
        jdbc.update("UPDATE flowora_project_milestone SET approval_status=?,version_no=version_no+1 WHERE organization_id=? AND id=? AND version_no=?",state,actor.organizationId(),milestoneId,body.version());
        BillingBasisView basis=null;
        if(body.approved())basis=upsertBasis(actor.organizationId(),value.projectId(),"MILESTONE",milestoneId,"Approved milestone "+value.name(),value.billingAmount(),project(value.projectId(),actor.organizationId()).currencyCode());
        jdbc.update("INSERT INTO flowora_audit_event(id,organization_id,actor_user_id,action_code,resource_type,resource_id,request_id,details_json) VALUES (?,?,?,?,'PROJECT',?,?,JSON_OBJECT('milestoneId',?,'reason',?))",UUID.randomUUID().toString(),actor.organizationId(),actor.userId(),body.approved()?"MILESTONE_APPROVED":"MILESTONE_REJECTED",value.projectId(),requestId,milestoneId,body.reason().trim());
        return basis;
    }

    public List<BillingBasisView> billingBasis(String organizationId, String projectId) {
        requireProject(organizationId, projectId);
        return jdbc.query("""
                SELECT id,project_id,basis_type,source_id,description,available_amount,billed_amount,currency_code,
                       status,approved_at FROM flowora_project_billing_basis
                WHERE organization_id=? AND project_id=? ORDER BY created_at
                """, (rs, row) -> basisRow(rs), organizationId, projectId);
    }

    @Transactional
    public InvoiceView generateInvoice(FloworaPrincipal actor, String projectId, String requestId, ProjectInvoiceCreate body) {
        finance.lockProjectBilling(actor.organizationId());
        Project project = project(projectId, actor.organizationId());
        if (project.customerId() == null || project.customerId().isBlank()) throw conflict("PROJECT_CUSTOMER_REQUIRED", Map.of());
        String financeRequestId = "project-invoice:" + required(requestId);
        List<String> existing = jdbc.query("SELECT id FROM flowora_finance_invoice WHERE organization_id=? AND request_id=?",
                (rs, row) -> rs.getString(1), actor.organizationId(), financeRequestId);
        if (!existing.isEmpty()) {
            var replay=finance.invoice(actor.organizationId(),existing.getFirst());
            boolean same=projectId.equals(replay.projectId()) && "SALES_INVOICE".equals(replay.documentType())
                    && replay.businessDate().equals(body.businessDate()) && replay.accountingDate().equals(body.accountingDate())
                    && replay.dueDate().equals(body.dueDate()) && replay.exchangeRateDate().equals(body.exchangeRateDate())
                    && replay.currencyCode().equals(body.currencyCode().trim().toUpperCase(java.util.Locale.ROOT))
                    && replay.exchangeRate().compareTo(body.exchangeRate())==0 && replay.lines().size()==body.lines().size();
            if(same)for(int i=0;i<body.lines().size();i++) {
                var line=replay.lines().get(i);var input=body.lines().get(i);
                same &= line.sources().size()==1 && "PROJECT_BILLING_BASIS".equals(line.sources().getFirst().sourceType())
                        && projectId.equals(line.sources().getFirst().sourceId()) && input.billingBasisId().equals(line.sources().getFirst().sourceLineId())
                        && line.unitPrice().compareTo(input.amount())==0 && line.taxRate().compareTo(input.taxRate()==null?BigDecimal.ZERO:input.taxRate())==0
                        && Objects.equals(line.accountCode(),clean(input.accountCode()));
            }
            if(!same)throw conflict("IDEMPOTENCY_KEY_REUSED",Map.of());
            return replay;
        }
        lifecycle.requireOpen(actor.organizationId(),projectId);
        List<InvoiceLineCreate> invoiceLines = new ArrayList<>();
        Set<String> basisIds = new HashSet<>();
        for (var line : body.lines()) {
            BillingBasisView basis = basisForUpdate(actor.organizationId(), projectId, line.billingBasisId());
            if (!basisIds.add(line.billingBasisId())) {
                throw conflict("PROJECT_BILLING_DUPLICATE_BASIS", Map.of("basisId", line.billingBasisId()));
            }
            BigDecimal remaining = basis.remainingAmount();
            if (line.amount().compareTo(remaining) > 0) throw conflict("PROJECT_BILLING_EXCEEDS_REMAINING", Map.of("remaining", remaining));
            BigDecimal taxRate = line.taxRate() == null ? BigDecimal.ZERO : line.taxRate();
            invoiceLines.add(new InvoiceLineCreate(null, basis.description(), BigDecimal.ONE, line.amount(),
                    BigDecimal.ZERO, taxRate, clean(line.accountCode()), projectId,
                    List.of(new InvoiceSourceCreate("PROJECT_BILLING_BASIS", projectId, basis.id(), BigDecimal.ONE, line.amount()))));
        }
        InvoiceView invoice = finance.createInvoice(actor, financeRequestId, new InvoiceCreate(
                "SALES_INVOICE", project.customerId(), projectId, null, body.businessDate(), body.accountingDate(),
                body.dueDate(), body.exchangeRateDate(), body.currencyCode(), body.exchangeRate(), invoiceLines));
        return invoice;
    }

    @Transactional(readOnly = true)
    public ProjectProfitView profit(String organizationId, String projectId) {
        Project project = project(projectId, organizationId);
        String baseCurrency=finance.projectBaseCurrency(organizationId);
        if(count("SELECT COUNT(*) FROM flowora_project_billing_basis WHERE organization_id=? AND project_id=? AND currency_code<>?",organizationId,projectId,project.currencyCode())>0)
            throw conflict("PROJECT_BILLING_CURRENCY_MISMATCH",Map.of());
        if(count("""
                SELECT COUNT(*) FROM flowora_journal_line l JOIN flowora_journal_entry e
                ON e.id=l.journal_entry_id AND e.organization_id=l.organization_id
                WHERE l.organization_id=? AND l.project_id=? AND e.status IN ('POSTED','REVERSED') AND e.base_currency_code<>?
                """,organizationId,projectId,baseCurrency)>0)throw conflict("PROJECT_LEDGER_CURRENCY_MISMATCH",Map.of());
        BigDecimal available = scalar("SELECT COALESCE(SUM(available_amount-billed_amount),0) FROM flowora_project_billing_basis WHERE organization_id=? AND project_id=?", organizationId, projectId);
        BigDecimal billed = scalar("SELECT COALESCE(SUM(billed_amount),0) FROM flowora_project_billing_basis WHERE organization_id=? AND project_id=?", organizationId, projectId);
        BigDecimal revenue = scalar("""
                SELECT COALESCE(SUM(l.base_credit-l.base_debit),0) FROM flowora_journal_line l
                JOIN flowora_journal_entry e ON e.id=l.journal_entry_id AND e.organization_id=l.organization_id JOIN flowora_account a
                  ON a.organization_id=l.organization_id AND a.code=l.account_code
                WHERE l.organization_id=? AND l.project_id=? AND e.status IN ('POSTED','REVERSED') AND a.account_type='REVENUE'
                """, organizationId, projectId);
        BigDecimal cost = scalar("""
                SELECT COALESCE(SUM(l.base_debit-l.base_credit),0) FROM flowora_journal_line l
                JOIN flowora_journal_entry e ON e.id=l.journal_entry_id AND e.organization_id=l.organization_id JOIN flowora_account a
                  ON a.organization_id=l.organization_id AND a.code=l.account_code
                WHERE l.organization_id=? AND l.project_id=? AND e.status IN ('POSTED','REVERSED') AND a.account_type='EXPENSE'
                """, organizationId, projectId);
        var laborAmounts=jdbc.query("SELECT currency_code,SUM(cost_amount) AS amount FROM flowora_timesheet WHERE organization_id=? AND project_id=? AND approval_status='APPROVED' GROUP BY currency_code ORDER BY currency_code",
                (rs,row)->new ProjectFinanceDtos.CurrencyAmount(rs.getString("currency_code"),rs.getBigDecimal("amount")),organizationId,projectId);
        BigDecimal labor=laborAmounts.stream().allMatch(a->project.currencyCode().equals(a.currencyCode()))
                ?laborAmounts.stream().map(ProjectFinanceDtos.CurrencyAmount::amount).reduce(BigDecimal.ZERO,BigDecimal::add):null;
        var readiness=lifecycle.readiness(organizationId,projectId);
        BigDecimal gross = revenue.subtract(cost);
        return new ProjectProfitView(projectId, project.currencyCode(), project.contractAmount(), available, billed,
                revenue, cost, labor, gross, project.currencyCode().equals(baseCurrency)?project.budgetRevenue().subtract(revenue):null,
                project.currencyCode().equals(baseCurrency)?project.budgetCost().subtract(cost):null,
                readiness.openTasks(), readiness.pendingApprovals(), readiness.unsettledInvoices(), readiness.eligible(), baseCurrency, laborAmounts,
                readiness.unbilledBases(),readiness.invalidBillingBases(),readiness.blockers(),readiness.openMilestones());
    }

    private BillingBasisView upsertBasis(String organizationId, String projectId, String type, String sourceId,
                                         String description, BigDecimal amount, String currency) {
        var project=project(projectId,organizationId);
        if("MILESTONE".equals(project.billingMode()) && !"MILESTONE".equals(type))throw conflict("PROJECT_BILLING_MODE_MISMATCH",Map.of());
        if(!project.currencyCode().equals(currency))
            throw conflict("PROJECT_BILLING_CURRENCY_MISMATCH",Map.of());
        jdbc.update("""
                INSERT INTO flowora_project_billing_basis
                (id,organization_id,project_id,basis_type,source_id,description,available_amount,currency_code,status,approved_at)
                VALUES (?,?,?,?,?,?,?,?,'AVAILABLE',CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE description=VALUES(description),
                status=CASE WHEN billed_amount=0 THEN 'AVAILABLE' WHEN billed_amount=GREATEST(available_amount,VALUES(available_amount)) THEN 'BILLED' ELSE 'PARTIAL' END,
                available_amount=GREATEST(available_amount,VALUES(available_amount)),approved_at=CURRENT_TIMESTAMP,version_no=version_no+1
                """, UUID.randomUUID().toString(), organizationId, projectId, type, sourceId, description, amount, currency);
        finance.refreshProjectBillingStatus(organizationId,projectId);
        return jdbc.query("""
                SELECT id,project_id,basis_type,source_id,description,available_amount,billed_amount,currency_code,status,approved_at
                FROM flowora_project_billing_basis WHERE organization_id=? AND basis_type=? AND source_id=?
                """, (rs, row) -> basisRow(rs), organizationId, type, sourceId).getFirst();
    }

    private BillingBasisView basisForUpdate(String organizationId, String projectId, String id) {
        List<BillingBasisView> values = jdbc.query("""
                SELECT id,project_id,basis_type,source_id,description,available_amount,billed_amount,currency_code,status,approved_at
                FROM flowora_project_billing_basis WHERE organization_id=? AND project_id=? AND id=? FOR UPDATE
                """, (rs, row) -> basisRow(rs), organizationId, projectId, id);
        if (values.isEmpty()) throw notFound("projectBillingBasis", id);
        return values.getFirst();
    }

    private static BillingBasisView basisRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal available = rs.getBigDecimal("available_amount");
        BigDecimal billed = rs.getBigDecimal("billed_amount");
        Timestamp approved = rs.getTimestamp("approved_at");
        return new BillingBasisView(rs.getString("id"), rs.getString("project_id"), rs.getString("basis_type"),
                rs.getString("source_id"), rs.getString("description"), available, billed,
                available.subtract(billed), rs.getString("currency_code"), rs.getString("status"),
                approved == null ? null : approved.toLocalDateTime());
    }

    private Project project(String id, String organizationId) {
        List<Project> values = jdbc.query("""
                SELECT id,customer_id,currency_code,contract_amount,budget_revenue,budget_cost,billing_mode
                FROM flowora_project WHERE id=? AND organization_id=?
                """, (rs, row) -> new Project(rs.getString("id"), rs.getString("customer_id"),
                rs.getString("currency_code"), rs.getBigDecimal("contract_amount"),
                rs.getBigDecimal("budget_revenue"), rs.getBigDecimal("budget_cost"),rs.getString("billing_mode")), id, organizationId);
        if (values.isEmpty()) throw notFound("project", id);
        return values.getFirst();
    }

    private void requireProject(String organizationId, String projectId) {
        project(projectId, organizationId);
    }

    private Timesheet timesheetForUpdate(String organizationId, String id) {
        List<Timesheet> values = jdbc.query("""
                SELECT id,project_id,billable,billable_amount,currency_code,approval_status
                FROM flowora_timesheet WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new Timesheet(rs.getString("id"), rs.getString("project_id"),
                rs.getBoolean("billable"), rs.getBigDecimal("billable_amount"), rs.getString("currency_code"),
                rs.getString("approval_status")), organizationId, id);
        if (values.isEmpty()) throw notFound("timesheet", id);
        return values.getFirst();
    }

    private Expense expenseForUpdate(String organizationId, String id) {
        List<Expense> values = jdbc.query("""
                SELECT id,project_id,billable,billable_amount,currency_code,approval_status
                FROM flowora_project_expense WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new Expense(rs.getString("id"), rs.getString("project_id"),
                rs.getBoolean("billable"), rs.getBigDecimal("billable_amount"), rs.getString("currency_code"),
                rs.getString("approval_status")), organizationId, id);
        if (values.isEmpty()) throw notFound("projectExpense", id);
        return values.getFirst();
    }

    private Milestone milestoneForUpdate(String organizationId, String id) {
        List<Milestone> values = jdbc.query("""
                SELECT id,project_id,name,status,billable,billing_amount,approval_status,version_no FROM flowora_project_milestone
                WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new Milestone(rs.getString("id"), rs.getString("project_id"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("billable"), rs.getBigDecimal("billing_amount"),rs.getString("approval_status"),rs.getLong("version_no")), organizationId, id);
        if (values.isEmpty()) throw notFound("projectMilestone", id);
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal scalar(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw conflict("IDEMPOTENCY_KEY_REQUIRED", Map.of());
        return value.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Project(String id, String customerId, String currencyCode, BigDecimal contractAmount,
                           BigDecimal budgetRevenue, BigDecimal budgetCost,String billingMode) {
    }

    private record Timesheet(String id, String projectId, boolean billable, BigDecimal billableAmount,
                             String currencyCode, String approvalStatus) {
    }

    private record Expense(String id, String projectId, boolean billable, BigDecimal billableAmount,
                           String currencyCode, String approvalStatus) {
    }

    private record Milestone(String id, String projectId, String name, String status, boolean billable,
                             BigDecimal billingAmount,String approvalStatus,long version) {
    }

}
