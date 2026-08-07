package com.flowora.erp.project;

import com.flowora.erp.common.api.ApiResponse;
import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.RequestIdFilter;
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

import java.util.List;

import static com.flowora.erp.project.ProjectDtos.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> list(@RequestParam(defaultValue = "") String query, @RequestParam(required = false) ProjectStatus status, @PageableDefault(size = 20) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.list(principal(authentication).organizationId(), query, status, pageable), request);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.create(principal(authentication), body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<ProjectResponse> summary(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.summary(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<ProjectResponse> changeStatus(@PathVariable String id, @Valid @RequestBody StatusChange body, Authentication authentication, HttpServletRequest request) {
        return response(service.changeStatus(principal(authentication), id, body.status(), RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/milestones")
    public ApiResponse<List<MilestoneResponse>> milestones(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.milestoneList(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/{id}/milestones")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<MilestoneResponse> createMilestone(@PathVariable String id, @Valid @RequestBody MilestoneCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createMilestone(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/tasks")
    public ApiResponse<PageResponse<TaskResponse>> tasks(@PathVariable String id, @PageableDefault(size = 50) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.taskList(principal(authentication).organizationId(), id, pageable), request);
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<TaskResponse> createTask(@PathVariable String id, @Valid @RequestBody TaskCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createTask(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<TaskResponse> changeTaskStatus(@PathVariable String taskId, @Valid @RequestBody TaskStatusChange body, Authentication authentication, HttpServletRequest request) {
        return response(service.changeTaskStatus(principal(authentication), taskId, body.status(), RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/timesheets")
    public ApiResponse<PageResponse<TimesheetResponse>> timesheets(@PathVariable String id, @PageableDefault(size = 50) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.timesheetList(principal(authentication).organizationId(), id, pageable), request);
    }

    @PostMapping("/{id}/timesheets")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<TimesheetResponse> createTimesheet(@PathVariable String id, @Valid @RequestBody TimesheetCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createTimesheet(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/expenses")
    public ApiResponse<PageResponse<ExpenseResponse>> expenses(@PathVariable String id, @PageableDefault(size = 50) Pageable pageable, Authentication authentication, HttpServletRequest request) {
        return response(service.expenseList(principal(authentication).organizationId(), id, pageable), request);
    }

    @PostMapping("/{id}/expenses")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ApiResponse<ExpenseResponse> createExpense(@PathVariable String id, @Valid @RequestBody ExpenseCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createExpense(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/budgets")
    public ApiResponse<List<BudgetResponse>> budgets(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.budgetList(principal(authentication).organizationId(), id), request);
    }

    @PostMapping("/{id}/budgets")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'FINANCE')")
    public ApiResponse<BudgetResponse> createBudget(@PathVariable String id, @Valid @RequestBody BudgetCreate body, Authentication authentication, HttpServletRequest request) {
        return response(service.createBudget(principal(authentication), id, body, RequestIdFilter.get(request)), request);
    }

    @GetMapping("/{id}/billing-basis")
    public ApiResponse<List<BillingBasisRow>> billingBasis(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return response(service.billingBasis(principal(authentication).organizationId(), id), request);
    }

    private FloworaPrincipal principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof FloworaPrincipal current) return current;
        throw new IllegalStateException("Authenticated FloworaPrincipal is required");
    }

    private <T> ApiResponse<T> response(T data, HttpServletRequest request) {
        return ApiResponse.of(data, RequestIdFilter.get(request));
    }
}
