package com.flowora.erp.workflow;

import com.flowora.erp.common.api.PageResponse;
import com.flowora.erp.common.api.ResourceNotFoundException;
import com.flowora.erp.common.api.WorkflowPermissionException;
import com.flowora.erp.common.api.WorkflowStateConflictException;
import com.flowora.erp.identity.FloworaPrincipal;
import com.flowora.erp.masterdata.OrganizationRepository;
import com.flowora.erp.masterdata.OrganizationEntity;
import com.flowora.erp.workflow.WorkflowDtos.ActionRequest;
import com.flowora.erp.workflow.WorkflowDtos.ActivityResponse;
import com.flowora.erp.workflow.WorkflowDtos.CommentRequest;
import com.flowora.erp.workflow.WorkflowDtos.CommentResponse;
import com.flowora.erp.workflow.WorkflowDtos.NotificationResponse;
import com.flowora.erp.workflow.WorkflowDtos.TaskRequest;
import com.flowora.erp.workflow.WorkflowDtos.TaskResponse;
import com.flowora.erp.workflow.WorkflowDtos.UnreadCount;
import com.flowora.erp.workflow.WorkflowDtos.WorkflowActionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService {
    private static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("10000.0000");

    private final WorkflowTaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;
    private final ActivityEventRepository activityRepository;
    private final AuditEventRepository auditRepository;
    private final ApprovalPolicy approvalPolicy;
    private final OrganizationRepository organizationRepository;

    public WorkflowService(
            WorkflowTaskRepository taskRepository,
            NotificationRepository notificationRepository,
            CommentRepository commentRepository,
            ActivityEventRepository activityRepository,
            AuditEventRepository auditRepository,
            ApprovalPolicy approvalPolicy,
            OrganizationRepository organizationRepository
    ) {
        this.taskRepository = taskRepository;
        this.notificationRepository = notificationRepository;
        this.commentRepository = commentRepository;
        this.activityRepository = activityRepository;
        this.auditRepository = auditRepository;
        this.approvalPolicy = approvalPolicy;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> inbox(FloworaPrincipal actor, Pageable pageable) {
        Collection<String> roles = actor.roles();
        Page<WorkflowTaskEntity> page = taskRepository.inbox(actor.organizationId(), actor.userId(), roles, pageable);
        return PageResponse.from(page.map(this::taskResponse));
    }

    @Transactional
    public TaskResponse createTask(FloworaPrincipal actor, TaskRequest request, String requestId) {
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        boolean requiresApproval = approvalPolicy.requiresApproval(
                request.resourceType(), amount, approvalThreshold(actor.organizationId())
        );
        String assigneeUserId = clean(request.assigneeUserId());
        String assigneeRole = requiresApproval && assigneeUserId.isBlank() ? approvalPolicy.approverRole(request.resourceType()) : null;
        WorkflowTaskStatus status = requiresApproval ? WorkflowTaskStatus.OPEN : WorkflowTaskStatus.APPROVED;
        WorkflowTaskEntity task = taskRepository.save(new WorkflowTaskEntity(
                actor.organizationId(), request.resourceType(), clean(request.resourceId()), clean(request.title()),
                clean(request.description()), amount, actor.userId(), emptyToNull(assigneeUserId), assigneeRole, status, request.dueAt()
        ));
        String action = requiresApproval ? "SUBMITTED" : "AUTO_APPROVED";
        record(task, actor, action, requiresApproval ? "Task submitted for approval" : "Task auto-approved below threshold", requestId);
        boolean notificationCreated = false;
        if (requiresApproval) {
            String recipient = assigneeUserId.isBlank() ? "ROLE:" + assigneeRole : assigneeUserId;
            notificationRepository.save(new NotificationEntity(
                    actor.organizationId(), recipient, "WORKFLOW_TASK", "Approval required", task.title()
            ));
            notificationCreated = true;
        }
        return taskResponse(task);
    }

    @Transactional
    public WorkflowActionResult act(FloworaPrincipal actor, String taskId, ActionRequest request, String requestId) {
        WorkflowTaskEntity task = taskRepository.findByIdAndOrganizationId(taskId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("workflowTask", taskId));
        ensureCanAct(actor, task, request.action());
        boolean notificationCreated;
        switch (request.action()) {
            case APPROVE -> {
                requireStatus(task, WorkflowTaskStatus.OPEN);
                task.approve();
                notificationCreated = notifyRequester(task, "APPROVED", "Workflow task approved");
                record(task, actor, "APPROVED", "Workflow task approved", requestId);
            }
            case REJECT -> {
                requireStatus(task, WorkflowTaskStatus.OPEN);
                task.reject();
                notificationCreated = notifyRequester(task, "REJECTED", "Workflow task rejected");
                record(task, actor, "REJECTED", "Workflow task rejected", requestId);
            }
            case TRANSFER -> {
                requireStatus(task, WorkflowTaskStatus.OPEN);
                String target = clean(request.transferToUserId());
                if (target.isBlank()) {
                    throw new WorkflowStateConflictException("Transfer requires a target user");
                }
                task.transfer(target);
                notificationRepository.save(new NotificationEntity(
                        actor.organizationId(), target, "WORKFLOW_TRANSFER", "Workflow task transferred", task.title()
                ));
                notificationCreated = true;
                record(task, actor, "TRANSFERRED", "Workflow task transferred", requestId);
            }
            case COMPLETE -> {
                requireStatus(task, WorkflowTaskStatus.APPROVED);
                task.complete();
                notificationCreated = notifyRequester(task, "COMPLETED", "Workflow task completed");
                record(task, actor, "COMPLETED", "Workflow task completed", requestId);
            }
            case CANCEL -> {
                if (!actor.userId().equals(task.requesterUserId()) && !actor.roles().contains("ADMIN")) {
                    throw new WorkflowPermissionException("Only the requester or administrator can cancel this task");
                }
                requireNotTerminal(task);
                task.cancel();
                notificationCreated = notifyRequester(task, "CANCELLED", "Workflow task cancelled");
                record(task, actor, "CANCELLED", "Workflow task cancelled", requestId);
            }
            default -> throw new WorkflowStateConflictException("Unsupported workflow action");
        }
        if (request.comment() != null && !request.comment().isBlank()) {
            addComment(actor, task.resourceType().name(), task.resourceId(), new CommentRequest(request.comment()), requestId);
        }
        taskRepository.save(task);
        return new WorkflowActionResult(taskResponse(task), request.action().name(), notificationCreated);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> notifications(FloworaPrincipal actor, Pageable pageable) {
        List<String> recipients = new ArrayList<>();
        recipients.add(actor.userId());
        actor.roles().forEach(role -> recipients.add("ROLE:" + role));
        return PageResponse.from(notificationRepository.inbox(actor.organizationId(), recipients, pageable).map(this::notificationResponse));
    }

    @Transactional
    public NotificationResponse markNotificationRead(FloworaPrincipal actor, String notificationId) {
        NotificationEntity entity = notificationRepository.findByIdAndOrganizationId(notificationId, actor.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("notification", notificationId));
        if (!actor.userId().equals(entity.recipientUserId()) && !actor.roles().contains(entity.recipientUserId().replace("ROLE:", ""))) {
            throw new WorkflowPermissionException("Notification does not belong to the current user");
        }
        entity.markRead();
        return notificationResponse(notificationRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(FloworaPrincipal actor) {
        List<String> recipients = new ArrayList<>();
        recipients.add(actor.userId());
        actor.roles().forEach(role -> recipients.add("ROLE:" + role));
        long count = notificationRepository.inbox(actor.organizationId(), recipients, Pageable.unpaged())
                .stream().filter(notification -> !notification.read()).count();
        return new UnreadCount(count);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(FloworaPrincipal actor, String resourceType, String resourceId, Pageable pageable) {
        return PageResponse.from(commentRepository.byResource(actor.organizationId(), clean(resourceType), clean(resourceId), pageable).map(this::commentResponse));
    }

    @Transactional
    public CommentResponse addComment(FloworaPrincipal actor, String resourceType, String resourceId, CommentRequest request, String requestId) {
        CommentEntity entity = commentRepository.save(new CommentEntity(
                actor.organizationId(), clean(resourceType), clean(resourceId), actor.userId(), clean(request.body())
        ));
        ActivityEventEntity activity = activityRepository.save(new ActivityEventEntity(
                actor.organizationId(), clean(resourceType), clean(resourceId), actor.userId(), "COMMENT_ADDED", "Comment added", "{}"
        ));
        auditRepository.save(new AuditEventEntity(actor.organizationId(), actor.userId(), "COMMENT_ADDED", resourceType, resourceId, requestId, "{}"));
        return commentResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> activities(FloworaPrincipal actor, String resourceType, String resourceId, Pageable pageable) {
        return PageResponse.from(activityRepository.byResource(actor.organizationId(), clean(resourceType), clean(resourceId), pageable).map(this::activityResponse));
    }

    private void ensureCanAct(FloworaPrincipal actor, WorkflowTaskEntity task, WorkflowAction action) {
        if (action == WorkflowAction.CANCEL && actor.userId().equals(task.requesterUserId())) {
            return;
        }
        boolean assignedUser = actor.userId().equals(task.assigneeUserId());
        boolean assignedRole = task.assigneeRole() != null && actor.roles().contains(task.assigneeRole());
        if (!assignedUser && !assignedRole && !actor.roles().contains("ADMIN")) {
            throw new WorkflowPermissionException("Current user is not assigned to this workflow task");
        }
    }

    private void requireStatus(WorkflowTaskEntity task, WorkflowTaskStatus expected) {
        if (task.status() != expected) {
            throw new WorkflowStateConflictException("Expected " + expected + " but was " + task.status());
        }
    }

    private void requireNotTerminal(WorkflowTaskEntity task) {
        if (task.status() == WorkflowTaskStatus.COMPLETED || task.status() == WorkflowTaskStatus.CANCELLED || task.status() == WorkflowTaskStatus.REJECTED) {
            throw new WorkflowStateConflictException("Workflow task is already terminal");
        }
    }

    private boolean notifyRequester(WorkflowTaskEntity task, String type, String title) {
        if (task.requesterUserId() == null || task.requesterUserId().equals(task.assigneeUserId())) {
            return false;
        }
        notificationRepository.save(new NotificationEntity(task.organizationId(), task.requesterUserId(), type, title, task.title()));
        return true;
    }

    private void record(WorkflowTaskEntity task, FloworaPrincipal actor, String action, String summary, String requestId) {
        activityRepository.save(new ActivityEventEntity(
                task.organizationId(), task.resourceType().name(), task.resourceId(), actor.userId(), action, summary, "{}"
        ));
        auditRepository.save(new AuditEventEntity(
                task.organizationId(), actor.userId(), action, task.resourceType().name(), task.resourceId(), requestId, "{}"
        ));
    }

    private BigDecimal approvalThreshold(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(OrganizationEntity::approvalThreshold)
                .orElse(DEFAULT_APPROVAL_THRESHOLD);
    }

    private TaskResponse taskResponse(WorkflowTaskEntity entity) {
        return new TaskResponse(entity.id(), entity.resourceType(), entity.resourceId(), entity.title(), entity.description(), entity.amount(), entity.requesterUserId(), entity.assigneeUserId(), entity.assigneeRole(), entity.status(), entity.dueAt(), entity.completedAt());
    }

    private NotificationResponse notificationResponse(NotificationEntity entity) {
        return new NotificationResponse(entity.id(), entity.type(), entity.title(), entity.message(), entity.read(), entity.createdAt());
    }

    private CommentResponse commentResponse(CommentEntity entity) {
        return new CommentResponse(entity.id(), entity.resourceType(), entity.resourceId(), entity.authorUserId(), entity.body(), entity.createdAt());
    }

    private ActivityResponse activityResponse(ActivityEventEntity entity) {
        return new ActivityResponse(entity.id(), entity.actionCode(), entity.summary(), entity.actorUserId(), entity.createdAt(), Map.of());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
