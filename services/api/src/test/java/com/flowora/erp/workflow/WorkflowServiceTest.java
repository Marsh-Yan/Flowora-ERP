package com.flowora.erp.workflow;

import com.flowora.erp.common.api.WorkflowStateConflictException;
import com.flowora.erp.common.api.WorkflowPermissionException;
import com.flowora.erp.identity.DataScope;
import com.flowora.erp.workflow.v2.WorkflowResourceAccessPolicy;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.OrganizationRepository;
import com.flowora.erp.workflow.WorkflowDtos.ActionRequest;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.WorkflowDtos.WorkflowActionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {
    @Mock
    private WorkflowTaskRepository taskRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ActivityEventRepository activityRepository;
    @Mock
    private AuditEventRepository auditRepository;
    @Mock
    private ApprovalPolicy approvalPolicy;
    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private WorkflowService service;

    private FloworaPrincipal actor;

    @BeforeEach
    void setUp() {
        actor = new FloworaPrincipal("user-1", "buyer@example.com", "Buyer", "org-a", "Demo", List.of("BUSINESS"));
        lenient().when(organizationRepository.findById("org-a")).thenReturn(Optional.empty());
        lenient().when(taskRepository.save(any(WorkflowTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(activityRepository.save(any(ActivityEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditRepository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void highValueTaskIsAssignedToManagementAndNotifiesApprover() {
        when(approvalPolicy.requiresApproval(any(), any(), any())).thenReturn(true);
        when(approvalPolicy.approverRole(WorkflowResourceType.PURCHASE_ORDER)).thenReturn("MANAGEMENT");

        var result = service.createTask(actor, new TaskRequest(
                WorkflowResourceType.PURCHASE_ORDER, "po-1", "Approve purchase order", "Supplier order", new BigDecimal("12000"), null, null
        ), "req-1");

        assertThat(result.status()).isEqualTo(WorkflowTaskStatus.OPEN);
        assertThat(result.assigneeRole()).isEqualTo("MANAGEMENT");
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(activityRepository).save(any(ActivityEventEntity.class));
        verify(auditRepository).save(any(AuditEventEntity.class));
    }

    @Test
    void lowValueTaskIsAutoApprovedWithoutNotification() {
        when(approvalPolicy.requiresApproval(any(), any(), any())).thenReturn(false);

        var result = service.createTask(actor, new TaskRequest(
                WorkflowResourceType.PURCHASE_ORDER, "po-2", "Small purchase", null, new BigDecimal("99"), null, null
        ), "req-2");

        assertThat(result.status()).isEqualTo(WorkflowTaskStatus.APPROVED);
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    void assignedManagerCanApproveAnOpenTaskAndNotifiesRequester() {
        FloworaPrincipal manager = new FloworaPrincipal("manager-1", "manager@example.com", "Manager", "org-a", "Demo", List.of("MANAGEMENT"));
        WorkflowTaskEntity task = new WorkflowTaskEntity(
                "org-a", WorkflowResourceType.PURCHASE_ORDER, "po-3", "Approve PO", null,
                new BigDecimal("12000"), "user-1", null, "MANAGEMENT", WorkflowTaskStatus.OPEN, null
        );
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));

        WorkflowActionResult result = service.act(manager, task.id(), new ActionRequest(WorkflowAction.APPROVE, null, null), "req-3");

        assertThat(result.task().status()).isEqualTo(WorkflowTaskStatus.APPROVED);
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(taskRepository).save(task);
    }

    @Test
    void cannotApproveAnAlreadyRejectedTask() {
        FloworaPrincipal manager = new FloworaPrincipal("manager-1", "manager@example.com", "Manager", "org-a", "Demo", List.of("MANAGEMENT"));
        WorkflowTaskEntity task = new WorkflowTaskEntity(
                "org-a", WorkflowResourceType.PURCHASE_ORDER, "po-4", "Rejected PO", null,
                new BigDecimal("12000"), "user-1", null, "MANAGEMENT", WorkflowTaskStatus.REJECTED, null
        );
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.act(manager, task.id(), new ActionRequest(WorkflowAction.APPROVE, null, null), "req-4"))
                .isInstanceOf(WorkflowStateConflictException.class);
        verify(taskRepository, never()).save(task);
    }

    @Test
    void alwaysPassesTheActorOrganizationToTaskLookup() {
        ArgumentCaptor<String> organizationCaptor = ArgumentCaptor.forClass(String.class);
        WorkflowTaskEntity task = new WorkflowTaskEntity(
                "org-a", WorkflowResourceType.GENERAL, "general-1", "General task", null,
                BigDecimal.ZERO, "user-1", "user-1", null, WorkflowTaskStatus.APPROVED, null
        );
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));

        service.act(actor, task.id(), new ActionRequest(WorkflowAction.COMPLETE, null, null), "req-5");

        verify(taskRepository).findByIdAndOrganizationId(any(), organizationCaptor.capture());
        assertThat(organizationCaptor.getValue()).isEqualTo("org-a");
    }

    @Test
    void eachActionRequiresItsPermissionEvenForAssignedAdministrativeRole() {
        for (var action : WorkflowAction.values()) {
            var task = task(WorkflowTaskStatus.OPEN);
            when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
            var actor = principal("user-1", List.of("ADMIN"), List.of());
            assertThatThrownBy(() -> service.act(actor, task.id(), new ActionRequest(action, "target", "blocked"), "request"))
                    .isInstanceOf(WorkflowPermissionException.class);
            assertThat(task.status()).isEqualTo(WorkflowTaskStatus.OPEN);
            verify(taskRepository, never()).save(task);
        }
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    @Test
    void actionPermissionsDoNotReplaceAssignmentAndRequesterBoundaries() {
        for (var action : WorkflowAction.values()) {
            var task = task(WorkflowTaskStatus.OPEN);
            when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
            var actor = principal("outsider", List.of("ADMIN"), List.of("workflow:approve", "workflow:delegate", "workflow:submit"));
            assertThatThrownBy(() -> service.act(actor, task.id(), new ActionRequest(action, "target", null), "request"))
                    .isInstanceOf(WorkflowPermissionException.class);
            assertThat(task.status()).isEqualTo(WorkflowTaskStatus.OPEN);
            verify(taskRepository, never()).save(task);
        }
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    @Test
    void customAdministrativePermissionCanCancelButDoesNotGrantApproval() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var admin = principal("outsider", List.of("CUSTOM"), List.of("workflow:admin"));
        assertThatThrownBy(() -> service.act(admin, task.id(), new ActionRequest(WorkflowAction.APPROVE, null, null), "request"))
                .isInstanceOf(WorkflowPermissionException.class);
        var result = service.act(admin, task.id(), new ActionRequest(WorkflowAction.CANCEL, null, null), "request");
        assertThat(result.task().status()).isEqualTo(WorkflowTaskStatus.CANCELLED);
    }

    @Test
    void bundledCommentRequiresCollaborationPermissionBeforeAnyMutation() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var approver = principal("user-1", List.of("CUSTOM"), List.of("workflow:approve"));
        assertThatThrownBy(() -> service.act(approver, task.id(), new ActionRequest(WorkflowAction.APPROVE, null, "blocked"), "request"))
                .isInstanceOf(WorkflowPermissionException.class);
        assertThat(task.status()).isEqualTo(WorkflowTaskStatus.OPEN);
        verify(taskRepository, never()).save(task);
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    @Test
    void bundledCommentRequiresLinkedResourceScopeBeforeAnyMutation() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var approver = principal("user-1", List.of("CUSTOM"), List.of("workflow:approve", "collaboration:comment"));
        var policy = mock(WorkflowResourceAccessPolicy.class);
        service.setResourceAccess(policy);
        doThrow(new WorkflowPermissionException("Out of scope")).when(policy).require(approver, "PURCHASE_ORDER", "po", "comment");
        assertThatThrownBy(() -> service.act(approver, task.id(), new ActionRequest(WorkflowAction.APPROVE, null, "blocked"), "request"))
                .isInstanceOf(WorkflowPermissionException.class);
        assertThat(task.status()).isEqualTo(WorkflowTaskStatus.OPEN);
        verify(taskRepository, never()).save(task);
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    private WorkflowTaskEntity task(WorkflowTaskStatus status) {
        return new WorkflowTaskEntity("org-a", WorkflowResourceType.PURCHASE_ORDER, "po", "Synthetic", null,
                new BigDecimal("12000"), "user-1", "user-1", null, status, null);
    }

    private FloworaPrincipal principal(String id, List<String> roles, List<String> permissions) {
        return new FloworaPrincipal(id, id + "@audit.invalid", "Synthetic", "org-a", "Org", "membership", null,
                DataScope.ALL, roles, permissions, false);
    }

    @Test void invalidTransferTargetNeverChangesTaskOrWritesAnySideEffect() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var policy = mock(WorkflowAssigneePolicy.class); service.setAssignees(policy);
        doThrow(WorkflowAssigneePolicy.invalid()).when(policy).require("org-a", "bad", WorkflowResourceType.PURCHASE_ORDER, "po", true);
        var delegate = principal("user-1", List.of("CUSTOM"), List.of("workflow:delegate"));
        assertThatThrownBy(() -> service.act(delegate, task.id(), new ActionRequest(WorkflowAction.TRANSFER, "bad", null), "request"))
                .isInstanceOf(com.flowora.erp.common.api.PlatformApiException.class);
        assertThat(task.assigneeUserId()).isEqualTo("user-1"); assertThat(task.status()).isEqualTo(WorkflowTaskStatus.OPEN);
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    @Test void eligibleTransferIsValidatedBeforeMovingAssignmentAndPersistingNotification() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var policy = mock(WorkflowAssigneePolicy.class); service.setAssignees(policy);
        var delegate = principal("user-1", List.of("CUSTOM"), List.of("workflow:delegate"));
        var result = service.act(delegate, task.id(), new ActionRequest(WorkflowAction.TRANSFER, " target ", null), "request");
        var order = org.mockito.Mockito.inOrder(policy, notificationRepository, taskRepository);
        order.verify(policy).require("org-a", "target", WorkflowResourceType.PURCHASE_ORDER, "po", true);
        order.verify(notificationRepository).save(any(NotificationEntity.class));
        order.verify(taskRepository).save(task);
        assertThat(result.task().assigneeUserId()).isEqualTo("target"); assertThat(result.task().assigneeRole()).isNull();
    }

    @Test void invalidExplicitCreationAssignmentNeverCreatesTaskOrSideEffects() {
        when(approvalPolicy.requiresApproval(any(), any(), any())).thenReturn(true);
        var policy = mock(WorkflowAssigneePolicy.class); service.setAssignees(policy);
        doThrow(WorkflowAssigneePolicy.invalid()).when(policy).require("org-a", "bad", WorkflowResourceType.PURCHASE_ORDER, "po", true);
        assertThatThrownBy(() -> service.createTask(actor, new TaskRequest(WorkflowResourceType.PURCHASE_ORDER,
                "po", "Blocked", null, new BigDecimal("12000"), "bad", null), "request"))
                .isInstanceOf(com.flowora.erp.common.api.PlatformApiException.class);
        verifyNoInteractions(taskRepository, notificationRepository, activityRepository, auditRepository, commentRepository);
    }

    @Test void missingTargetPolicyCannotSilentlyAllowExplicitAssignment() {
        var task = task(WorkflowTaskStatus.OPEN);
        when(taskRepository.findByIdAndOrganizationId(task.id(), "org-a")).thenReturn(Optional.of(task));
        var delegate = principal("user-1", List.of("CUSTOM"), List.of("workflow:delegate"));
        assertThatThrownBy(() -> service.act(delegate, task.id(), new ActionRequest(WorkflowAction.TRANSFER, "target", null), "request"))
                .isInstanceOf(com.flowora.erp.common.api.PlatformApiException.class);
        verifyNoInteractions(notificationRepository, activityRepository, auditRepository, commentRepository);
    }
}
