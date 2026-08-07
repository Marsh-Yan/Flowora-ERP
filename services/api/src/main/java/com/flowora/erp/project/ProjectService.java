package com.flowora.erp.project;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.sales.SalesOrderEntity;
import com.flowora.erp.sales.SalesOrderRepository;
import com.flowora.erp.workflow.ActivityEventEntity;
import com.flowora.erp.workflow.ActivityEventRepository;
import com.flowora.erp.workflow.AuditEventEntity;
import com.flowora.erp.workflow.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.flowora.erp.project.ProjectDtos.*;

@Service
public class ProjectService {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final ProjectTaskRepository tasks;
    private final TimesheetRepository timesheets;
    private final ProjectExpenseRepository expenses;
    private final ProjectBudgetRepository budgets;
    private final CustomerRepository customers;
    private final SalesOrderRepository salesOrders;
    private final ActivityEventRepository activities;
    private final AuditEventRepository audits;

    public ProjectService(ProjectRepository projects, MilestoneRepository milestones, ProjectTaskRepository tasks,
                          TimesheetRepository timesheets, ProjectExpenseRepository expenses, ProjectBudgetRepository budgets,
                          CustomerRepository customers, SalesOrderRepository salesOrders, ActivityEventRepository activities,
                          AuditEventRepository audits) {
        this.projects = projects;
        this.milestones = milestones;
        this.tasks = tasks;
        this.timesheets = timesheets;
        this.expenses = expenses;
        this.budgets = budgets;
        this.customers = customers;
        this.salesOrders = salesOrders;
        this.activities = activities;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(String organizationId, String query, ProjectStatus status, Pageable pageable) {
        Page<ProjectEntity> page = projects.search(organizationId, query == null ? "" : query.trim(), status, pageable);
        return PageResponse.from(page.map(this::projectResponse));
    }

    @Transactional
    public ProjectResponse create(FloworaPrincipal actor, ProjectCreate body, String requestId) {
        String customerId = body.customerId();
        SalesOrderEntity order = null;
        if (customerId != null && !customerId.isBlank()) {
            String requestedCustomerId = customerId;
            customers.findByIdAndOrganizationId(customerId, actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER", requestedCustomerId));
        }
        if (body.salesOrderId() != null && !body.salesOrderId().isBlank()) {
            order = salesOrders.findByIdAndOrganizationId(body.salesOrderId(), actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("SALES_ORDER", body.salesOrderId()));
            if (customerId != null && !customerId.equals(order.customerId())) {
                throw new IllegalArgumentException("Sales order customer does not match project customer");
            }
            customerId = order.customerId();
        }
        String currency = normalizeCurrency(body.currencyCode(), order == null ? null : order.currencyCode());
        ProjectEntity saved = projects.save(new ProjectEntity(
                actor.organizationId(), projectNumber(), body.name().trim(), body.description(), customerId,
                blankToNull(body.salesOrderId()), blankOrDefault(body.managerUserId(), actor.userId()), body.targetDate(),
                money(body.budgetRevenue()), money(body.budgetCost()), currency
        ));
        record(actor, "PROJECT_CREATED", saved.id(), "Project " + saved.number() + " created", requestId);
        return projectResponse(saved);
    }

    @Transactional
    public ProjectResponse changeStatus(FloworaPrincipal actor, String id, ProjectStatus status, String requestId) {
        ProjectEntity project = project(actor.organizationId(), id);
        project.transitionTo(status);
        projects.save(project);
        record(actor, "PROJECT_STATUS_CHANGED", id, "Project status changed to " + status, requestId);
        return projectResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse summary(String organizationId, String id) {
        return projectResponse(project(organizationId, id));
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> milestoneList(String organizationId, String projectId) {
        project(organizationId, projectId);
        return milestones.findByOrganizationIdAndProjectIdOrderBySequenceNoAsc(organizationId, projectId).stream().map(this::milestoneResponse).toList();
    }

    @Transactional
    public MilestoneResponse createMilestone(FloworaPrincipal actor, String projectId, MilestoneCreate body, String requestId) {
        project(actor.organizationId(), projectId);
        int sequence = body.sequenceNo() == null ? milestones.findFirstByOrganizationIdAndProjectIdOrderBySequenceNoDesc(actor.organizationId(), projectId).map(item -> item.sequenceNo() + 1).orElse(1) : body.sequenceNo();
        MilestoneEntity saved = milestones.save(new MilestoneEntity(actor.organizationId(), projectId, body.name().trim(), sequence, body.targetDate()));
        record(actor, "MILESTONE_CREATED", projectId, "Milestone " + saved.name() + " created", requestId);
        return milestoneResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> taskList(String organizationId, String projectId, Pageable pageable) {
        project(organizationId, projectId);
        return PageResponse.from(tasks.findByOrganizationIdAndProjectIdOrderByDueDateAscCreatedAtAsc(organizationId, projectId, pageable).map(task -> taskResponse(organizationId, task)));
    }

    @Transactional
    public TaskResponse createTask(FloworaPrincipal actor, String projectId, TaskCreate body, String requestId) {
        project(actor.organizationId(), projectId);
        if (body.milestoneId() != null) {
            MilestoneEntity milestone = milestones.findByIdAndOrganizationId(body.milestoneId(), actor.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("MILESTONE", body.milestoneId()));
            if (!projectId.equals(milestone.projectId())) throw new IllegalArgumentException("Milestone does not belong to project");
        }
        ProjectTaskEntity saved = tasks.save(new ProjectTaskEntity(actor.organizationId(), projectId, blankToNull(body.milestoneId()), body.title().trim(), body.description(),
                blankOrDefault(body.assigneeUserId(), actor.userId()), body.priority() == null ? ProjectTaskPriority.MEDIUM : body.priority(),
                body.dueDate(), money(body.estimatedHours())));
        record(actor, "TASK_CREATED", projectId, "Task " + saved.title() + " created", requestId);
        return taskResponse(actor.organizationId(), saved);
    }

    @Transactional
    public TaskResponse changeTaskStatus(FloworaPrincipal actor, String taskId, ProjectTaskStatus status, String requestId) {
        ProjectTaskEntity task = tasks.findByIdAndOrganizationId(taskId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_TASK", taskId));
        task.transitionTo(status);
        tasks.save(task);
        record(actor, "TASK_STATUS_CHANGED", task.projectId(), "Task status changed to " + status, requestId);
        return taskResponse(actor.organizationId(), task);
    }

    @Transactional(readOnly = true)
    public PageResponse<TimesheetResponse> timesheetList(String organizationId, String projectId, Pageable pageable) {
        project(organizationId, projectId);
        return PageResponse.from(timesheets.findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(organizationId, projectId, pageable).map(this::timesheetResponse));
    }

    @Transactional
    public TimesheetResponse createTimesheet(FloworaPrincipal actor, String projectId, TimesheetCreate body, String requestId) {
        ProjectEntity project = project(actor.organizationId(), projectId);
        validateTask(actor.organizationId(), projectId, body.taskId());
        TimesheetEntity saved = timesheets.save(new TimesheetEntity(actor.organizationId(), projectId, blankToNull(body.taskId()), actor.userId(), body.workDate(), body.hours(), body.costRate(), body.billingRate(), body.billable(), normalizeCurrency(body.currencyCode(), project.currencyCode()), body.note()));
        record(actor, "TIMESHEET_CREATED", projectId, "Timesheet logged for " + body.hours() + " hours", requestId);
        return timesheetResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> expenseList(String organizationId, String projectId, Pageable pageable) {
        project(organizationId, projectId);
        return PageResponse.from(expenses.findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(organizationId, projectId, pageable).map(this::expenseResponse));
    }

    @Transactional
    public ExpenseResponse createExpense(FloworaPrincipal actor, String projectId, ExpenseCreate body, String requestId) {
        ProjectEntity project = project(actor.organizationId(), projectId);
        validateTask(actor.organizationId(), projectId, body.taskId());
        ProjectExpenseEntity saved = expenses.save(new ProjectExpenseEntity(actor.organizationId(), projectId, blankToNull(body.taskId()), actor.userId(), body.expenseDate(), body.category().trim(), body.amount(), body.billable(), normalizeCurrency(body.currencyCode(), project.currencyCode()), body.description()));
        record(actor, "EXPENSE_CREATED", projectId, "Expense logged for " + body.amount(), requestId);
        return expenseResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> budgetList(String organizationId, String projectId) {
        project(organizationId, projectId);
        return budgets.findByOrganizationIdAndProjectIdOrderByCategoryAsc(organizationId, projectId).stream().map(this::budgetResponse).toList();
    }

    @Transactional
    public BudgetResponse createBudget(FloworaPrincipal actor, String projectId, BudgetCreate body, String requestId) {
        ProjectEntity project = project(actor.organizationId(), projectId);
        ProjectBudgetEntity saved = budgets.save(new ProjectBudgetEntity(actor.organizationId(), projectId, body.category().trim(), body.amount(), normalizeCurrency(body.currencyCode(), project.currencyCode()), body.note()));
        record(actor, "BUDGET_CREATED", projectId, "Budget " + body.category() + " created", requestId);
        return budgetResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BillingBasisRow> billingBasis(String organizationId, String projectId) {
        project(organizationId, projectId);
        List<BillingBasisRow> rows = new ArrayList<>();
        timesheets.findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(organizationId, projectId).stream().filter(TimesheetEntity::billable)
                .forEach(item -> rows.add(new BillingBasisRow("TIMESHEET", item.id(), item.workDate(), item.taskId(), item.note(), item.hours(), item.billableAmount(), item.currencyCode(), item.userId())));
        expenses.findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(organizationId, projectId).stream().filter(ProjectExpenseEntity::billable)
                .forEach(item -> rows.add(new BillingBasisRow("EXPENSE", item.id(), item.expenseDate(), item.taskId(), item.description(), BigDecimal.ONE, item.billableAmount(), item.currencyCode(), item.userId())));
        return rows.stream().sorted(Comparator.comparing(BillingBasisRow::date).reversed()).toList();
    }

    private ProjectEntity project(String organizationId, String id) {
        return projects.findByIdAndOrganizationId(id, organizationId).orElseThrow(() -> new ResourceNotFoundException("PROJECT", id));
    }

    private void validateTask(String organizationId, String projectId, String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        ProjectTaskEntity task = tasks.findByIdAndOrganizationId(taskId, organizationId).orElseThrow(() -> new ResourceNotFoundException("PROJECT_TASK", taskId));
        if (!projectId.equals(task.projectId())) throw new IllegalArgumentException("Task does not belong to project");
    }

    private ProjectResponse projectResponse(ProjectEntity project) {
        List<ProjectTaskEntity> projectTasks = tasks.findByOrganizationIdAndProjectIdOrderByDueDateAscCreatedAtAsc(project.organizationId(), project.id());
        List<TimesheetEntity> projectTimesheets = timesheets.findByOrganizationIdAndProjectIdOrderByWorkDateDescCreatedAtDesc(project.organizationId(), project.id());
        List<ProjectExpenseEntity> projectExpenses = expenses.findByOrganizationIdAndProjectIdOrderByExpenseDateDescCreatedAtDesc(project.organizationId(), project.id());
        BigDecimal actualCost = projectTimesheets.stream().map(TimesheetEntity::costAmount).reduce(BigDecimal.ZERO, BigDecimal::add).add(projectExpenses.stream().map(ProjectExpenseEntity::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal actualHours = projectTimesheets.stream().map(TimesheetEntity::hours).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal billable = projectTimesheets.stream().map(TimesheetEntity::billableAmount).reduce(BigDecimal.ZERO, BigDecimal::add).add(projectExpenses.stream().map(ProjectExpenseEntity::billableAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        int completed = (int) projectTasks.stream().filter(item -> item.status() == ProjectTaskStatus.DONE).count();
        BigDecimal progress = projectTasks.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(completed).multiply(ONE_HUNDRED).divide(BigDecimal.valueOf(projectTasks.size()), 2, RoundingMode.HALF_UP);
        return new ProjectResponse(project.id(), project.number(), project.name(), project.description(), project.customerId(), project.salesOrderId(), project.managerUserId(), project.targetDate(), project.budgetRevenue(), project.budgetCost(), project.currencyCode(), project.status(), progress, actualCost, actualHours, billable, completed, projectTasks.size());
    }

    private TaskResponse taskResponse(String organizationId, ProjectTaskEntity task) {
        BigDecimal actual = timesheets.findByOrganizationIdAndTaskIdOrderByWorkDateAsc(organizationId, task.id()).stream().map(TimesheetEntity::hours).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TaskResponse(task.id(), task.projectId(), task.milestoneId(), task.title(), task.description(), task.assigneeUserId(), task.priority(), task.dueDate(), task.status(), task.estimatedHours(), actual);
    }

    private MilestoneResponse milestoneResponse(MilestoneEntity item) { return new MilestoneResponse(item.id(), item.projectId(), item.name(), item.sequenceNo(), item.targetDate(), item.status()); }
    private TimesheetResponse timesheetResponse(TimesheetEntity item) { return new TimesheetResponse(item.id(), item.projectId(), item.taskId(), item.userId(), item.workDate(), item.hours(), item.costAmount(), item.billableAmount(), item.billable(), item.currencyCode(), item.note()); }
    private ExpenseResponse expenseResponse(ProjectExpenseEntity item) { return new ExpenseResponse(item.id(), item.projectId(), item.taskId(), item.userId(), item.expenseDate(), item.category(), item.amount(), item.billableAmount(), item.billable(), item.currencyCode(), item.description()); }
    private BudgetResponse budgetResponse(ProjectBudgetEntity item) { return new BudgetResponse(item.id(), item.projectId(), item.category(), item.amount(), item.currencyCode(), item.note()); }

    private void record(FloworaPrincipal actor, String action, String resourceId, String summary, String requestId) {
        activities.save(new ActivityEventEntity(actor.organizationId(), "PROJECT", resourceId, actor.userId(), action, summary, null));
        audits.save(new AuditEventEntity(actor.organizationId(), actor.userId(), action, "PROJECT", resourceId, requestId, null));
    }

    private static String projectNumber() { return "PRJ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String blankOrDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String normalizeCurrency(String requested, String fallback) { return requested == null || requested.isBlank() ? (fallback == null ? "USD" : fallback) : requested.toUpperCase(); }
}
