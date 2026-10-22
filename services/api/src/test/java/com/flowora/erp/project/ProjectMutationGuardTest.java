package com.flowora.erp.project;

import com.flowora.erp.common.api.PlatformApiException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.CustomerRepository;
import com.flowora.erp.sales.SalesOrderRepository;
import com.flowora.erp.workflow.ActivityEventRepository;
import com.flowora.erp.workflow.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.flowora.erp.finance.v2.FinanceLedgerService.conflict;

@ExtendWith(MockitoExtension.class)
class ProjectMutationGuardTest {
    @Mock ProjectRepository projects; @Mock MilestoneRepository milestones; @Mock ProjectTaskRepository tasks;
    @Mock TimesheetRepository timesheets; @Mock ProjectExpenseRepository expenses; @Mock ProjectBudgetRepository budgets;
    @Mock CustomerRepository customers; @Mock SalesOrderRepository salesOrders;
    @Mock ActivityEventRepository activities; @Mock AuditEventRepository audits; @Mock ProjectLifecycleService lifecycle;
    @InjectMocks ProjectService service;
    final FloworaPrincipal actor=new FloworaPrincipal("manager","manager","Manager","org","Org",List.of("PROJECT_MANAGER"));
    @Test void closedProjectsRejectEveryOperationalCreateBeforeWritingOrAuditing() {
        doThrow(conflict("PROJECT_CLOSED",Map.of())).when(lifecycle).requireOpen("org","project");
        List<Supplier<?>> actions=List.of(()->service.createMilestone(actor,"project",null,"request"),()->service.createTask(actor,"project",null,"request"),()->service.createTimesheet(actor,"project",null,"request"),()->service.createExpense(actor,"project",null,"request"),()->service.createBudget(actor,"project",null,"request"));
        for(var action:actions)assertThatThrownBy(action::get).isInstanceOf(PlatformApiException.class).extracting("code").isEqualTo("PROJECT_CLOSED");
        verifyNoInteractions(projects,milestones,tasks,timesheets,expenses,budgets,activities,audits);
    }
    @Test void taskStatusCannotChangeInsideAClosedProject() {
        var task=new ProjectTaskEntity("org","project",null,"Delivery",null,"manager",ProjectTaskPriority.MEDIUM,null,BigDecimal.ONE);
        when(tasks.findByIdAndOrganizationId(task.id(),"org")).thenReturn(Optional.of(task));
        doThrow(conflict("PROJECT_CLOSED",Map.of())).when(lifecycle).requireOpen("org","project");
        assertThatThrownBy(()->service.changeTaskStatus(actor,task.id(),ProjectTaskStatus.IN_PROGRESS,"request")).isInstanceOf(PlatformApiException.class);
        assertThat(task.status()).isEqualTo(ProjectTaskStatus.TODO);verify(tasks,never()).save(any());verifyNoInteractions(activities,audits);
    }
    @Test void blockedCompletionDoesNotProduceAStatusChangeAuditOrResponse() {
        doThrow(conflict("PROJECT_CLOSE_BLOCKED",Map.of("blockers",List.of("OPEN_TASKS")))).when(lifecycle).changeStatus("org","project",ProjectStatus.COMPLETED);
        assertThatThrownBy(()->service.changeStatus(actor,"project",ProjectStatus.COMPLETED,"request")).isInstanceOf(PlatformApiException.class);
        verifyNoInteractions(projects,activities,audits);
    }

    @Test void completedMilestoneRejectsNewLinkedTasksBeforeAnyTaskWrite() {
        var project=new ProjectEntity("org","PRJ","Delivery",null,null,null,"manager",java.time.LocalDate.of(2026,10,22),BigDecimal.ZERO,BigDecimal.ZERO,"USD");
        when(projects.findByIdAndOrganizationId("project","org")).thenReturn(Optional.of(project));doThrow(conflict("MILESTONE_ALREADY_COMPLETED",Map.of())).when(lifecycle).requireMilestoneTaskTarget("org","project","milestone");
        assertThatThrownBy(()->service.createTask(actor,"project",new ProjectDtos.TaskCreate("Task",null,"milestone",null,ProjectTaskPriority.MEDIUM,null,BigDecimal.ONE),"request")).isInstanceOf(PlatformApiException.class);verifyNoInteractions(tasks,activities,audits);
    }
    @Test void failedMilestoneTransitionCannotProduceASuccessAudit() {
        doThrow(conflict("OPTIMISTIC_LOCK_CONFLICT",Map.of())).when(lifecycle).changeMilestoneStatus("org","milestone",MilestoneStatus.COMPLETED,2);
        assertThatThrownBy(()->service.changeMilestoneStatus(actor,"milestone",new ProjectDtos.MilestoneStatusChange(MilestoneStatus.COMPLETED,2L),"request")).isInstanceOf(PlatformApiException.class);verifyNoInteractions(milestones,activities,audits);
    }

    @Test void milestoneModeRejectsBillableOperatingRegistrationBeforeSavingSourceRecords() {
        var project=new ProjectEntity("org","PRJ","Delivery",null,null,null,"manager",java.time.LocalDate.of(2026,10,22),BigDecimal.ZERO,BigDecimal.ZERO,"USD");when(projects.findByIdAndOrganizationId("project","org")).thenReturn(Optional.of(project));doThrow(conflict("PROJECT_BILLING_MODE_MISMATCH",Map.of())).when(lifecycle).requireOperatingBilling("org","project");
        assertThatThrownBy(()->service.createTimesheet(actor,"project",new ProjectDtos.TimesheetCreate(null,java.time.LocalDate.of(2026,10,22),BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN,true,"USD",null),"request")).isInstanceOf(PlatformApiException.class);
        assertThatThrownBy(()->service.createExpense(actor,"project",new ProjectDtos.ExpenseCreate(null,java.time.LocalDate.of(2026,10,22),"TRAVEL",BigDecimal.TEN,true,"USD",null),"request")).isInstanceOf(PlatformApiException.class);verifyNoInteractions(timesheets,expenses,activities,audits);
    }
}
